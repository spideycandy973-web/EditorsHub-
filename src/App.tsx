import React from 'react';
import { UIProvider } from './app/context';
import { Root } from './app/Root';

/** EditorsHub v4.0.0 — fully new application layer over the real engine. */
export default function App() {
  return (
    <UIProvider>
      <Root />
    </UIProvider>
  );
}
