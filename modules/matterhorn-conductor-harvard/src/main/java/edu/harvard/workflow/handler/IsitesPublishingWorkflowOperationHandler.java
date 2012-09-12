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
package edu.harvard.workflow.handler;

import org.opencastproject.job.api.JobContext;
import org.opencastproject.mediapackage.MediaPackage;
import org.opencastproject.mediapackage.MediaPackageElement;
import org.opencastproject.mediapackage.MediaPackageElements;
import org.opencastproject.mediapackage.MediaPackageException;
import org.opencastproject.mediapackage.MediaPackageReference;
import org.opencastproject.workflow.api.AbstractWorkflowOperationHandler;
import org.opencastproject.workflow.api.WorkflowInstance;
import org.opencastproject.workflow.api.WorkflowOperationException;
import org.opencastproject.workflow.api.WorkflowOperationResult;
import org.opencastproject.workflow.api.WorkflowOperationResult.Action;

import edu.harvard.matterhorn.IsitesService;

import org.apache.commons.lang.StringUtils;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * @author doh385
 * 
 */
public class IsitesPublishingWorkflowOperationHandler extends AbstractWorkflowOperationHandler {

  private static final Logger logger = LoggerFactory.getLogger(IsitesPublishingWorkflowOperationHandler.class);

  private IsitesService isitesService = null;

  /** The configuration options for this handler */
  private static final SortedMap<String, String> CONFIG_OPTIONS;

  static {
    CONFIG_OPTIONS = new TreeMap<String, String>();
    CONFIG_OPTIONS
            .put("source-tags", "Publish any mediapackage elements with one of these (whitespace separated) tags");
  }

  /**
   * {@inheritDoc}
   * 
   * @see org.opencastproject.workflow.api.WorkflowOperationHandler#getConfigurationOptions()
   */
  @Override
  public SortedMap<String, String> getConfigurationOptions() {
    return CONFIG_OPTIONS;
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.opencastproject.workflow.api.AbstractWorkflowOperationHandler#activate(org.osgi.service.component.ComponentContext
   * )
   */
  @Override
  protected void activate(ComponentContext ctx) {
    BundleContext bundleCtx = ctx.getBundleContext();
  }

  protected void deactivate() {
  }

  protected MediaPackage getMediaPackageForIsitesPublishing(MediaPackage current, List<String> tags)
          throws MediaPackageException {
    MediaPackage mp = (MediaPackage) current.clone();

    List<MediaPackageElement> keep = new ArrayList<MediaPackageElement>();

    // Keep those elements that have been identified in the tags
    keep.addAll(Arrays.asList(current.getElementsByTags(tags)));

    // Explicitly keep all security policies
    keep.addAll(Arrays.asList(mp.getAttachments(MediaPackageElements.XACML_POLICY)));

    // Mark everything that is set for removal
    List<MediaPackageElement> removals = new ArrayList<MediaPackageElement>();
    for (MediaPackageElement element : mp.getElements()) {
      if (!keep.contains(element)) {
        removals.add(element);
      }
    }

    // Fix references and flavors
    for (MediaPackageElement element : mp.getElements()) {

      if (removals.contains(element))
        continue;

      // Is the element referencing anything?
      MediaPackageReference reference = element.getReference();
      if (reference != null) {
        Map<String, String> referenceProperties = reference.getProperties();
        MediaPackageElement referencedElement = mp.getElementByReference(reference);

        // if we are distributing the referenced element, everything is fine. Otherwise...
        if (referencedElement != null && removals.contains(referencedElement)) {

          // Follow the references until we find a flavor
          MediaPackageElement parent = null;
          while ((parent = current.getElementByReference(reference)) != null) {
            if (parent.getFlavor() != null && element.getFlavor() == null) {
              element.setFlavor(parent.getFlavor());
            }
            if (parent.getReference() == null)
              break;
            reference = parent.getReference();
          }

          // Done. Let's cut the path but keep references to the mediapackage itself
          if (reference != null && reference.getType().equals(MediaPackageReference.TYPE_MEDIAPACKAGE))
            element.setReference(reference);
          else if (reference != null && (referenceProperties == null || referenceProperties.size() == 0))
            element.clearReference();
          else {
            // Ok, there is more to that reference than just pointing at an element. Let's keep the original,
            // you never know.
            removals.remove(referencedElement);
            referencedElement.setURI(null);
            referencedElement.setChecksum(null);
          }
        }
      }
    }

    // Remove everything we don't want to add to publish
    for (MediaPackageElement element : removals) {
      mp.remove(element);
    }
    return mp;
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.opencastproject.workflow.api.AbstractWorkflowOperationHandler#start(org.opencastproject.workflow.api.
   * WorkflowInstance, org.opencastproject.job.api.JobContext)
   */
  @Override
  public WorkflowOperationResult start(WorkflowInstance workflowInstance, JobContext context)
          throws WorkflowOperationException {
    logger.info("Starting iSites publishing workflow");
    if (this.isitesService == null) {
      throw new WorkflowOperationException("iSites Service is null");
    }

    MediaPackage mediaPackageFromWorkflow = workflowInstance.getMediaPackage();

    // Check which tags have been configured
    String tags = workflowInstance.getCurrentOperation().getConfiguration("source-tags");
    if (StringUtils.trimToNull(tags) == null) {
      logger.warn("No source tags have been specified, so nothing will be added to the search index");
      return createResult(mediaPackageFromWorkflow, Action.CONTINUE);
    }

    List<String> tagSet = asList(tags);

    try {
      MediaPackage mediaPackageForIsites = getMediaPackageForIsitesPublishing(mediaPackageFromWorkflow, tagSet);
      if (mediaPackageForIsites == null) {
        createResult(mediaPackageForIsites, Action.CONTINUE);
      }
      logger.info("Publishing media package {} to iSites", mediaPackageForIsites);
      this.isitesService.publish(mediaPackageForIsites);

      logger.info("Finished iSites publishing workflow");
      return createResult(mediaPackageFromWorkflow, Action.CONTINUE);
    } catch (Throwable t) {
      throw new WorkflowOperationException(t);
    }
  }

  /**
   * @param isitesService
   *          the isitesService to set
   */
  public void setIsitesService(IsitesService isitesService) {
    logger.info("IsitesService registered with IsitesPublishingWorkflowHandler");
    this.isitesService = isitesService;
  }

  public void removeIsitesService(IsitesService isitesService) {
    this.isitesService = null;
    logger.info("IsitesService removed from IsitesPublishingWorkflowHandler");
  }

}
