```
Copyright (C) 2020 by phantom
Email: admin@phvntom.tech
This file is under MIT License, see http://phvntom.tech/LICENSE.txt
Recording some concepts of sbt 

[Referencs]
    https://www.scala-sbt.org/1.x/docs/index.html
```

### *What is sbt ?*

sbt is a build tool for Scala, Java, and more.

### Directory Structure
sbt uses the same directory structure as Maven for source files by default

```
./ <Base Directory>
  src/
    main/
      resources/
         <files to include in main jar here>
      scala/
         <main Scala sources>
      scala-2.12/
         <main Scala 2.12 specific sources>
      java/
         <main Java sources>
    test/
      resources
         <files to include in test jar here>
      scala/
         <test Scala sources>
      scala-2.12/
         <test Scala 2.12 specific sources>
      java/
         <test Java sources>
```


