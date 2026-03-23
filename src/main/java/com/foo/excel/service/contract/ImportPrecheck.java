package com.foo.excel.service.contract;

import java.util.Optional;

/**
 * Import-specific metadata precheck contract executed before expensive row parsing.
 *
 * <p>Use this for blockers that depend only on import metadata and should reject the whole import
 * immediately.
 *
 * @param <M> import-specific metadata type
 */
public interface ImportPrecheck<M extends ImportMetadata> {

  /**
   * Returns a structured user-facing Korean failure when the import should be blocked before
   * parsing.
   *
   * @param metadata validated import metadata
   * @return blocking failure, or empty when the import may proceed
   */
  Optional<ImportPrecheckFailure> check(M metadata);
}
