import groovy.json.JsonSlurper;
import java.io.*;

public final class JmhVis {
	def classes = [:]
	PrintStream out

	public static void main(String[] args) {
		def slurper = new JsonSlurper()
		def jmh = new JmhVis()
		for(arg in args) {
			def result = slurper.parse(new File(arg));

			for(x in result)
				jmh.addBenchmark(x)
		}
		jmh.setOutput(new PrintStream(new File("index.html")));
		jmh.writeHtml();

		jmh.setOutput(System.out);
		jmh.writeUnicode();
		jmh.writeTerminal();
	}

	public void addBenchmark(benchmark) {
		String name = benchmark.benchmark;
		String className = name.substring(0, name.lastIndexOf("."))
		String methodName = name.substring(name.lastIndexOf(".") + 1)

		if(classes[className] == null)
			classes[className] = [:]
		if(classes[className][methodName] == null)
			classes[className][methodName] = []
		classes[className][methodName].add(0, benchmark) // add at beginning
	}

	public void setOutput(PrintStream out) {
		this.out = out
	}

	public void writeHtml() {
		out.println("<style>body {font-family: monospace;}</style>")
		out.println('<div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr))">')
		for(c in classes) {
			out.printf(
				'<section style=""><h2>%s</h2>%n',
				c.key.replaceFirst(/.*\./, "")
			)
			double max = c.value.values().stream()
				.flatMap({it.stream()})
				.mapToDouble({it.primaryMetric.score.toDouble()})
				.max()
				.orElse(0)
			for(m in c.value) {
				int age = 0
				for(version in m.value) {
					writeMetric(m.key, version.primaryMetric, max, age++)
				}
			}
			out.println("</section>")
		}
		out.println("</div><footer>Versions are ordered from newest to oldest</footer>")
	}

	private static final int columns = Integer.getInteger("COLUMNS", 70) - 10
	private static int scaleNumText(double num, double max) {
		return (int)(num / max * columns)
	}

	public void writeUnicode() {
		writeText(false)
	}

	public void writeTerminal() {
		writeText(true)
	}

	private static final def chars = ["░", "▒"]
	private static final def textColors = [91, 94, 92, 31]
	private static final def textGrayColors = [236, 234, 235, 233]
	private void writeText(boolean colored) {
		for(c in classes) {
			out.printf(
				colored ? '%n\033[7m%s\033[0m%n%n' : '%n%s%n%n',
				c.key.replaceFirst(/.*\./, "")
			)
			double max = c.value.values().stream()
				.flatMap({it.stream()})
				.mapToDouble({it.primaryMetric.score.toDouble()})
				.max()
				.orElse(0)
			for(m in c.value) {
				int age = 0
				for(version in m.value) {
					def metric = version.primaryMetric
					if(age == 0)
						out.printf(" %s (%s)%n", m.key, metric.scoreUnit)
					if(colored)
						out.printf("\033[48;5;%dm%s\033[0m",
							textGrayColors[age % textGrayColors.size()],
							" ".repeat(scaleNumText(metric.score - metric.scoreError, max)))
					else
						out.print(chars[age % chars.size()].repeat(scaleNumText(metric.score - metric.scoreError, max)))
					if(colored)
						out.printf("\033[%dm%s\033[0m",
							textColors[age % textColors.size()],
							"█".repeat(scaleNumText(metric.scoreError * 2, max))
						)
					else
						out.print("█".repeat(scaleNumText(metric.scoreError * 2, max)))
					out.printf(" %.1f%n", metric.score)
					age++
				}
			}
		}
		out.println("Versions are ordered from newest to oldest");
	}

	private static int scaleNumHtml(double num, double max) {
		return (int)(num / max * 80)
	}

	private static final def colors = ["orange", "dodgerblue", "lawngreen", "red"]
	private static final def grayColors = ["#ddd", "#bbb", "#ccc", "#aaa"]
	private void writeMetric(String name, metric, double max, int age) {
		out.println('<div style="padding: %px">')
		if(age == 0) {
			out.printf(
				'<div>%s <sub>(%s)</sub></div>%n',
				name, metric.scoreUnit
			);
		}
		out.printf(
'''
<div style="display: inline-block; background-color: %s; width: %d%%; margin: 0">
&nbsp;
</div><div style="display: inline-block; background-color: %s; width: %d%%; margin: 0; text-align: center"><b>%.1f</b></div>
''',
			grayColors[age % grayColors.size()],
			scaleNumHtml(metric.score - metric.scoreError, max),
			colors[age % colors.size()], scaleNumHtml(2*metric.scoreError, max), metric.score
		)
		out.println("</div>");
	}
}
