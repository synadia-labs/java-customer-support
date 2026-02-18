# SSL Test Project

Testing SSL Error Raising, Version 1.0.2 

### Prerequisites

* Java 11, 17 or 21
* Run from the ssl directory in this project.

### Environment

1\. The JNATS Java Client Version is set in the `build.gradle` and `pom.xml` as `2.20.0`
You can manually change the `build.gradle` or `pom.xml` to change this or set an environment variable, see below.
During the test run, the JNats Version will be printed.

2\. If you want full server output,
you can set `SHOW_SERVER` variable manually in the [SslTests.java](src/test/java/io/nats/client/impl/SslTests.java) class

**The environment variables...**

| OS      | JNats Version                            | Show Server Setting                |
|---------|------------------------------------------|------------------------------------|
| Unix    | `export JNATS_VERSION=major.minor.patch` | `export SSLTESTS.SHOW.SERVER=true` |
| Windows | `set JNATS_VERSION=major.minor.patch`    | `set SSLTESTS.SHOW.SERVER=true`    |

### Test class: 
[SslTests.java](src/test/java/io/nats/client/impl/SslTests.java)

### Command Line

#### Gradle
> Note: Gradle 8.14 or later is required.

Run all tests:
```
gradlew test
```

Run individual tests
```
gradlew test --tests SslTests.testConnectFailsFromSslContext
gradlew test --tests SslTests.testConnectFailsCertAlreadyExpired
gradlew test --tests SslTests.testReconnectFailsAfterCertExpires
gradlew test --tests SslTests.testForceReconnectFailsAfterCertExpires
```

#### Maven

Run all tests:
```
mvn test
```

Run individual tests
```
mvn -Dtest=SslTests#testConnectFailsFromSslContext test
mvn -Dtest=SslTests#testConnectFailsCertAlreadyExpired test
mvn -Dtest=SslTests#testReconnectFailsAfterCertExpires test
mvn -Dtest=SslTests#testForceReconnectFailsAfterCertExpires test
```
