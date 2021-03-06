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
package org.opencastproject.search.impl.persistence;

/**
 * Presents exception that occurs while storing/retrieving series from permanent storage or from index.
 */
public class SearchServiceDatabaseException extends Exception {

  /**
   * UUID
   */
  private static final long serialVersionUID = -5638740649716499805L;

  /**
   * Create exception.
   */
  public SearchServiceDatabaseException() {
  }

  /**
   * Create exception with a message.
   * 
   * @param message
   */
  public SearchServiceDatabaseException(String message) {
    super(message);
  }

  /**
   * Create exception with a cause.
   * 
   * @param cause
   */
  public SearchServiceDatabaseException(Throwable cause) {
    super(cause);
  }

  /**
   * Create exception with a message and a cause.
   * 
   * @param message
   * @param cause
   */
  public SearchServiceDatabaseException(String message, Throwable cause) {
    super(message, cause);
  }

}
