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
   * Returns a structured user-facing Korean failure when the upload should be blocked before
   * parsing.
   *
   * @param metaData validated upload metadata
   * @return blocking failure, or empty when the upload may proceed
   */
  Optional<UploadPrecheckFailure> check(M metaData);
}
