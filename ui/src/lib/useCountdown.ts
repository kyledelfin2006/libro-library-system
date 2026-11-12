import { useEffect, useState } from 'react';

/** Counts down whole seconds from `start`; null means no countdown. Used for 429 Retry-After. */
export function useCountdown(start: number | null): number | null {
  const [left, setLeft] = useState<number | null>(start);

  useEffect(() => {
    setLeft(start);
    if (start === null || start <= 0) return;
    const timer = window.setInterval(() => {
      setLeft((v) => (v !== null && v > 1 ? v - 1 : 0));
    }, 1000);
    return () => window.clearInterval(timer);
  }, [start]);

  return left;
}
