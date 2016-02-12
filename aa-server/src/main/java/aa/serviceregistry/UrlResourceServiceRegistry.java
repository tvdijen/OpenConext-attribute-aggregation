package aa.serviceregistry;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.MalformedURLException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.util.Collections.singletonList;
import static java.util.concurrent.Executors.newScheduledThreadPool;
import static org.springframework.http.HttpHeaders.IF_MODIFIED_SINCE;
import static org.springframework.http.HttpMethod.HEAD;
import static org.springframework.http.HttpStatus.NOT_MODIFIED;

public class UrlResourceServiceRegistry extends ClassPathResourceServiceRegistry {

  private final BasicAuthenticationUrlResource urlResource;

  private final RestTemplate restTemplate = new RestTemplate();
  private final int period;
  private final String spRemotePath;

  public UrlResourceServiceRegistry(
      String username,
      String password,
      String spRemotePath,
      int period) throws MalformedURLException {
    super(false);
    this.urlResource = new BasicAuthenticationUrlResource(spRemotePath, username, password);
    this.spRemotePath = spRemotePath;
    this.period = period;

    SimpleClientHttpRequestFactory requestFactory = (SimpleClientHttpRequestFactory) restTemplate.getRequestFactory();
    requestFactory.setConnectTimeout(5 * 1000);

    newScheduledThreadPool(1).scheduleAtFixedRate(this::refreshMetataData, period, period, TimeUnit.MINUTES);
    super.initializeMetadata();
  }

  @Override
  protected List<Resource> getResources() {
    LOG.debug("Fetching SP metadata entries from {}", spRemotePath);
    return singletonList(urlResource);
  }

  @Override
  protected void initializeMetadata() {
    if (urlResource.isModified(period)) {
      super.initializeMetadata();
    } else {
      LOG.debug("Not refreshing SP metadata. Not modified");
    }
  }

  private void refreshMetataData() {
    try {
      this.initializeMetadata();
    } catch (RuntimeException e) {
      LOG.error("Error in refreshing metadata", e);
      //don't rethrow as this will stop the scheduled thread pool
    }

  }
}
