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
package edu.harvard.matterhorn.utils;

import org.opencastproject.mediapackage.Attachment;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElementFlavor;
import org.opencastproject.mediapackage.Track;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;

/**
 * @author doh385
 * 
 */
public final class IsitesUtils {

  private static Logger logger = LoggerFactory.getLogger(IsitesUtils.class);

  private IsitesUtils() {

  }

  public static Document mediaPackageToMediaRss(MediaPackage mediaPackage) {
    Element rss = new Element("rss");
    Document doc = new Document(rss);
    Namespace mediaNS = Namespace.getNamespace("media", "http://search.yahoo.com/mrss");
    Namespace vptNS = Namespace.getNamespace("vpt", "http://video.isites.harvard.edu/vpt/");
    rss.addNamespaceDeclaration(mediaNS);
    rss.addNamespaceDeclaration(vptNS);

    Element item = new Element("item");
    rss.addContent(item);

    Element title = new Element("title");
    SimpleDateFormat recordingSdf = new SimpleDateFormat("MMMM d");
    title.addContent(mediaPackage.getTitle() + " " + recordingSdf.format(mediaPackage.getDate()));
    item.addContent(title);

    Element externalId = new Element("externalId", vptNS);
    externalId.addContent(mediaPackage.getIdentifier().toString());
    item.addContent(externalId);

    Element duration = new Element("duration", vptNS);
    long seconds = mediaPackage.getDuration() / 1000;
    duration.addContent(String.format("%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, (seconds % 60)));
    item.addContent(duration);

    Attachment[] attachments = mediaPackage.getAttachments(MediaPackageElementFlavor
            .parseFlavor("presentation/player+preview"));
    if (attachments.length > 0) {
      Element thumbnail = new Element("thumbnail", mediaNS);
      thumbnail.setAttribute("url", attachments[0].getURI().toString());
      item.addContent(thumbnail);
    }

    Element group = new Element("group", mediaNS);
    item.addContent(group);

    for (Track track : mediaPackage.getTracks(MediaPackageElementFlavor.parseFlavor("presenter/delivery"))) {
      Element content = new Element("content", mediaNS);
      content.setAttribute("type", "presenter");
      content.setAttribute("url", track.getURI().toString());
      group.addContent(content);
    }

    for (Track track : mediaPackage.getTracks(MediaPackageElementFlavor.parseFlavor("presentation/delivery"))) {
      Element content = new Element("content", mediaNS);
      content.setAttribute("type", "presentation");
      content.setAttribute("url", track.getURI().toString());
      group.addContent(content);
    }

    return doc;
  }

  public static String encodeAsMD5(String input) {
    StringBuffer hexString = new StringBuffer();
    try {
      MessageDigest algorithm = MessageDigest.getInstance("MD5");
      byte[] md = algorithm.digest(input.getBytes());
      for (int i = 0; i < md.length; i++) {
        String hex = Integer.toHexString(0xFF & md[i]);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
    } catch (NoSuchAlgorithmException e) {
      logger.error("Failed to encode " + input + " as MD5", e);
    }
    return hexString.toString();
  }

}
