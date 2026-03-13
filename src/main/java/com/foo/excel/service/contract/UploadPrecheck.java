package com.foo.excel.service.contract;

import java.util.Optional;

/**
 * Template-specific upload-level precheck contract executed before expensive row parsing.
 *
 * <p>Use this for blockers that depend only on upload metadata and should reject the whole upload
 * immediately.
 *
 * @param <M> template-specific metadata type
 */
public interface UploadPrecheck<M extends MetaData> {

  /**
   * Returns a user-facing Korean failure message when the upload should be blocked before parsing.
   *
   * @param metaData validated upload metadata
   * @return blocking message, or empty when the upload may proceed
   */
  Optional<String> check(M metaData);
}
