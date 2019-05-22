## Testing Akka Build with Scalac build pipelining


```
$ cd /code/akka

$ sbt clean argsFile test:argsFile
```

```
# Using https://github.com/scala/scala/pull/8077

$ qscala -J-Xmx4G -J-verbose:gc

scala> def build(s: PipelineMain.BuildStrategy): Unit = { import java.nio.file._; import scala.tools.nsc._; import scala.collection.JavaConverters._; val path = "/code/akka";val base = Paths.get("/tmp/pipeline-test"); Files.createDirectories(base); val pipelineSettings = PipelineMain.defaultSettings.copy(useJars = true, cacheMacro = true, cachePlugin = true, stripExternalClassPath = true); val argsFiles = Files.walk(Paths.get(path)).iterator().asScala.filter(_.getFileName.toString.endsWith(".args")).toList; val main = new PipelineMainClass(argsFiles, pipelineSettings.copy(strategy = s, logDir = Some(base.resolve(s.toString)))); main.process()}
build: (s: scala.tools.nsc.PipelineMain.BuildStrategy)Unit

scala> val strategies = List(PipelineMain.Traditional, PipelineMain.Pipeline, PipelineMain.OutlineTypePipeline)
strategies: List[Product with Serializable with scala.tools.nsc.PipelineMain.BuildStrategy] = List(Traditional, Pipeline, OutlineTypePipeline)

scala> strategies.foreach(build(_))

...

```

