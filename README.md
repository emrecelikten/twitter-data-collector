# Twitter data collector

Tool for collecting data from Twitter streaming API. Requires Java 8. Built on top of [hbc-core](https://github.com/twitter/hbc) from Twitter.

It managed to run for a few months without any crashes, but it is still a work in progress. Bug reports are very welcome.

## Usage

First you need to get an API key from Twitter.

You need [SBT](http://www.scala-sbt.org/) to build the project. After launching SBT in the project folder, compile the binaries as

```
$ sbt universal:packageBin
```

You will see a zip file under `target/universal` folder. After extracting it, you will see three directories: `bin`, `conf` and `lib`. Before running the tool, you need to insert your Twitter API key into `application.conf` in `conf` directory. The configuration file also includes some other settings that you might want to adjust.

After completing the configuration, you can run it with:

```
$ ./bin/twitterdatacollector -Dconfig=application.conf
```

Make sure that you have lots of disk space.

## Future tasks

Tests.
Error handling.

## Testing

In order to run the tests, you need to provide your own configuration with valid keys for Twitter. To do so, start the tests with testConfiguration environment variable set. e.g.

```
sbt -DtestConfiguration="application-test-myName.conf"
```
