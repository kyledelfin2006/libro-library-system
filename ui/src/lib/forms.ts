import { useEffect, type RefObject } from 'react';
import { ApiError } from '../api/client';

/** Unexpected-error copy: always offers the request ID as a reference (plan section 5.2). */
export function describeError(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.status >= 500 || e.status === 0) {
      return e.requestId && e.status !== 0
        ? `Something went wrong on our side. Reference: ${e.requestId.slice(0, 8)}.`
        : e.message;
    }
    return e.message;
  }
  return 'Something went wrong. Try again.';
}

/** After a failed submission, moves focus to the first field marked invalid. */
export function useFocusFirstInvalid(form: RefObject<HTMLFormElement | null>, errorVersion: unknown): void {
  useEffect(() => {
    if (!errorVersion) return;
    form.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
  }, [form, errorVersion]);
}
