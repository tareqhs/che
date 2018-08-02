/*
 * Copyright (c) 2012-2018 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.everrest;

import static org.eclipse.che.everrest.ETagResponseFilter.EntityType.JSON_SERIALIZABLE;
import static org.eclipse.che.everrest.ETagResponseFilter.EntityType.STRING;
import static org.eclipse.che.everrest.ETagResponseFilter.EntityType.UNKNOWN;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.Provider;
import org.eclipse.che.dto.server.JsonSerializable;

/**
 * Filter implementing {@link org.everrest.core.ResponseFilter} in order to generate ETag for
 * clients that want to use conditional requests. It is applying on GET method and JSON content type
 * only.
 *
 * @author Florent Benoit
 */
@Provider
public class ETagResponseFilter implements ContainerResponseFilter {

  public enum EntityType {
    JSON_SERIALIZABLE,
    STRING,
    UNKNOWN
  }

  /**
   * Filter the given container response
   *
   * @param containerResponse the response to use
   */
  @Override
  public void filter(
      ContainerRequestContext requestContext, ContainerResponseContext containerResponse)
      throws IOException {

    // get entity of the response
    Object entity = containerResponse.getEntity();

    // no entity, skip
    if (entity == null) {
      return;
    }

    // Only handle JSON content
    if (!MediaType.APPLICATION_JSON_TYPE.equals(containerResponse.getMediaType())) {
      return;
    }

    // Get the request
    Request request = requestContext.getRequest();

    // manage only GET requests
    if (!HttpMethod.GET.equals(request.getMethod())) {
      return;
    }

    // calculate hash with MD5
    HashFunction hashFunction = Hashing.md5();
    Hasher hasher = hashFunction.newHasher();
    boolean hashingSuccess = true;

    // Manage a list
    if (entity instanceof List) {
      List<?> entities = (List) entity;
      for (Object simpleEntity : entities) {
        hashingSuccess = addHash(simpleEntity, hasher);
        if (!hashingSuccess) {
          break;
        }
      }
    } else {
      hashingSuccess = addHash(entity, hasher);
    }

    // if we're able to handle the hash
    if (hashingSuccess) {

      // get result of the hash
      HashCode hashCode = hasher.hash();

      // Create the entity tag
      EntityTag entityTag = new EntityTag(hashCode.toString());

      // Check the etag
      Response.ResponseBuilder builder = request.evaluatePreconditions(entityTag);

      // not modified ?
      if (builder != null) {
        containerResponse.setStatusInfo(Status.NOT_MODIFIED);
        containerResponse.getHeaders().putSingle(HttpHeaders.ETAG, entityTag);
      } else {
        // it has been changed, so send response with new ETag and entity
        Response.ResponseBuilder responseBuilder =
            Response.fromResponse(containerResponse.getResponse()).tag(entityTag);
        containerResponse.setResponse(responseBuilder.build());
      }
    }
  }

  /**
   * Helper method to add entity to hash. If there is an invalid entity type it will return false
   *
   * @param entity the entity object to analyze and extract JSON for hashing it
   * @param hasher the hasher used to add the hashes
   */
  protected boolean addHash(Object entity, Hasher hasher) {
    // get entity type
    EntityType entityType = getElementType(entity);

    // check
    if (entityType == UNKNOWN) {
      // unknown entity type, cannot perform hash
      return false;
    }
    // add hash if all is OK
    try {
      hasher.putString(getJson(entity, entityType), Charset.defaultCharset());
    } catch (RuntimeException e) {
      return false;
    }
    return true;
  }

  /**
   * Helper method to retrieving the JSON content based on the entity type
   *
   * @param entity the object to analyze
   * @return the JSON string or null if it's an unknown type
   */
  protected String getJson(Object entity, EntityType entityType) {
    switch (entityType) {
      case JSON_SERIALIZABLE:
        return ((JsonSerializable) entity).toJson();
      case STRING:
        return (String) entity;
      default:
        return null;
    }
  }

  /**
   * Helper method for getting the type of the JSON entity
   *
   * @param entity the entity object
   * @return the type of the element
   */
  protected EntityType getElementType(Object entity) {
    if (JsonSerializable.class.isAssignableFrom(entity.getClass())) {
      return JSON_SERIALIZABLE;
    }

    if (String.class.isAssignableFrom(entity.getClass())) {
      return STRING;
    }

    return UNKNOWN;
  }
}
