/**
 *  Copyright 2009, 2010 The Regents of the University of California
 *  Licensed under the Educational Community License, Version 2.0
 *  (the "License"); you may not use this file except in compliance
 *  with the License. You may obtain a copy of the License at
 *
 *  http://www.osedu.org/licenses/ECL-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an "AS IS"
 *  BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 *  or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 *
 */
package edu.harvard.matterhorn;

import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.metadata.dublincore.DublinCoreCatalog;
import org.opencastproject.metadata.dublincore.DublinCoreValue;
import org.opencastproject.series.api.SeriesService;

import edu.harvard.matterhorn.utils.IsitesUtils;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.jdom.Document;
import org.jdom.output.XMLOutputter;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author doh385
 * 
 */
public class IsitesService implements ManagedService {

  private static Logger logger = LoggerFactory.getLogger(IsitesService.class);
  private Map<String, String> isitesSecrets = new HashMap<String, String>();
  private String isitesPublishUrl;

  private SeriesService seriesService;

  /**
   * @param seriesService
   *          the seriesService to set
   */
  public void setSeriesService(SeriesService seriesService) {
    this.seriesService = seriesService;
  }

  public void activate(ComponentContext ctx) throws Exception {
    logger.info("IsitesService activated using declarative services");
  }

  public void deactivate(ComponentContext ctx) throws Exception {
    logger.info("IsitesService deactivated using declarative services");
  }

  public void publish(MediaPackage mediaPackage) {
    logger.info("Publishing media package to iSites");

    Document doc = IsitesUtils.mediaPackageToMediaRss(mediaPackage);
    String seriesId = mediaPackage.getSeries();
    if (seriesId == null) {
      logger.error("Failed to publish media package to iSites; No series associated with media package "
              + mediaPackage.getIdentifier().toString());
      return;
    }

    String[] seriesIdParts = seriesId.split("_");
    String school = seriesIdParts[0];
    String secret = this.isitesSecrets.get(school);
    String courseKey = "uuid_" + seriesIdParts[1] + "_" + seriesIdParts[2] + "_" + seriesIdParts[3] + "_"
            + (seriesIdParts.length == 5 ? seriesIdParts[4] : "") + "_";

    try {
      DublinCoreCatalog catalog = this.seriesService.getSeries(seriesId);
      List<DublinCoreValue> shortTitleValues = catalog.get(DublinCoreCatalog.PROPERTY_IS_PART_OF);
      if (shortTitleValues != null && shortTitleValues.size() > 0) {
        courseKey += shortTitleValues.get(0).getValue().replaceAll(" ", "").toLowerCase();
      }

      XMLOutputter outputter = new XMLOutputter();
      StringWriter writer = new StringWriter();
      outputter.output(doc, writer);
      String rss = writer.toString();
      String md5Input = school + "-" + secret + rss.trim();
      String authKey = IsitesUtils.encodeAsMD5(md5Input);

      DefaultHttpClient httpclient = new DefaultHttpClient();
      HttpPost httpPost = new HttpPost(this.isitesPublishUrl);
      List<NameValuePair> nvps = new ArrayList<NameValuePair>();
      nvps.add(new BasicNameValuePair("courseId", courseKey));
      nvps.add(new BasicNameValuePair("school", school));
      nvps.add(new BasicNameValuePair("secret", secret));
      nvps.add(new BasicNameValuePair("rss", rss));
      nvps.add(new BasicNameValuePair("authkey", authKey));
      nvps.add(new BasicNameValuePair("md5input", md5Input));
      httpPost.setEntity(new UrlEncodedFormEntity(nvps));
      HttpResponse response = httpclient.execute(httpPost);

      logger.info(response.getStatusLine().toString());

      // Log iSites autopub response
      HttpEntity entity = response.getEntity();
      StringWriter responseWriter = new StringWriter();
      IOUtils.copy(entity.getContent(), responseWriter);

      ObjectMapper jsonMapper = new ObjectMapper();
      String responseString = responseWriter.toString().trim();
      JsonNode json = jsonMapper.readValue(responseString, JsonNode.class);
      JsonNode error = json.get("errorMsg");
      if (error != null) {
        String message = error.getTextValue();
        logger.error("Failed to publish to iSites " + mediaPackage.getIdentifier().toString() + " : " + school + " : "
                + courseKey + " : " + message);
      }
      logger.info(responseString);

      EntityUtils.consume(entity);
    } catch (Exception e) {
      logger.error("Failed to publish to iSites " + mediaPackage.getIdentifier().toString() + " : " + school + " : "
              + courseKey, e);
    }
  }

  public void updated(Dictionary props) throws ConfigurationException {
    logger.info("IsitesService received updated configuration");
    if (props == null) {
      return;
    }
    String secretsValue = (String) props.get("isites.secrets");
    String[] secrets = secretsValue.split(",");
    for (String secret : secrets) {
      String[] secretParts = secret.split("-");
      this.isitesSecrets.put(secretParts[0], secretParts[1]);
      logger.info("Updated isites secret " + secretParts[1] + " for school " + secretParts[0]);
    }

    this.isitesPublishUrl = (String) props.get("isites.publish.url");
    logger.info("Updated isites publish url with " + this.isitesPublishUrl);
  }

}
