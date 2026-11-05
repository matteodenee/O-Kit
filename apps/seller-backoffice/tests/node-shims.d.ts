declare module 'node:assert/strict' {
  const assert: {
    deepEqual(actual: unknown, expected: unknown): void;
    equal(actual: unknown, expected: unknown): void;
    rejects(callback: () => Promise<unknown>, expected?: RegExp): Promise<void>;
  };
  export default assert;
}

declare module 'node:test' {
  const test: (name: string, callback: () => void | Promise<void>) => void;
  export default test;
}
