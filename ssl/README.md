# SSL Test Project

Testing SSL Error Raising

### Setting the JNATS Java Client Version

Set the version as needed:

#### Gradle

`build.gradle` 

```
testImplementation "io.nats:jnats:2.20.0"
```

#### Maven

`pom.xml`
```xml
<dependency>
    <groupId>io.nats</groupId>
    <artifactId>jnats</artifactId>
    <version>2.20.0</version>
    <scope>test</scope>
</dependency>
```

### Test class: 
[SslTests.java](src/test/java/io/nats/client/impl/SslTests.java)

### Environment

Run from the ssl directory.

If you want full server output, add the `SSLTESTS.SHOW.SERVER` environment variable like so:

You can also set the `SHOW_SERVER` variable manually in the [SslTests.java](src/test/java/io/nats/client/impl/SslTests.java) class

**Unix**
```
export SSLTESTS.SHOW.SERVER=true
```

**Windows**
```
set SSLTESTS.SHOW.SERVER=true
```

### Command Line

#### Gradle
Pre-requisites: Gradle 8.14 or later
```
gradlew clean test
```

#### Maven
```
mvn clean test
```
