# foursquare-data-collector

Tool for collecting Foursquare data from Twitter streaming API. WARNING: WORK IN PROGRESS

### Current status:
Is able to download Swarm application check-ins from Twitter streaming API.

### Future tasks:
Tests.
Error handling.

## Testing

In order to run the tests, you need to provide your own configuration with valid keys for Twitter and Foursquare. To do so, start the tests with testConfiguration environment variable set. e.g.

```
sbt -DtestConfiguration="application-test-myName.conf"
```
