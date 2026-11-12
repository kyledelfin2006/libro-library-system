/** Temporary landing for pages built in later plan steps; keeps the shell navigable without faking data. */
export function Placeholder({ title, step }: { title: string; step: number }) {
  return (
    <section>
      <h1>{title}</h1>
      <p className="muted">This page is built in Step {step} of the UI execution plan.</p>
    </section>
  );
}
