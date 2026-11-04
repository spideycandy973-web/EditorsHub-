import React from 'react';
import { UIProvider } from './um/context';
import { Root } from './um/Root';

/** EditorsHub — Ultra-style launcher + timeline editor over the real engine. */
export default function App() {
  return (
    <UIProvider>
      <Root />
    </UIProvider>
  );
}
