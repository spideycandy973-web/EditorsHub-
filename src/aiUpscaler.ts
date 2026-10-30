type UpscalerEngine = {
  upscale: (image: HTMLImageElement, options: {
    output: 'base64';
    patchSize: number;
    padding: number;
    progress: (amount: number) => void;
  }) => Promise<string>;
};

let enginePromise: Promise<UpscalerEngine> | null = null;

async function createEngine(): Promise<UpscalerEngine> {
  const tf = await import('@tensorflow/tfjs');
  try {
    if (await tf.setBackend('webgl')) await tf.ready();
    else { await tf.setBackend('cpu'); await tf.ready(); }
  } catch {
    await tf.setBackend('cpu');
    await tf.ready();
  }
  const [{ default: Upscaler }, { default: defaultModel }] = await Promise.all([import('upscaler'), import('@upscalerjs/default-model')]);
  const localModel = (runtime: Parameters<typeof defaultModel>[0]) => ({
    ...defaultModel(runtime),
    // Vite copies the model JSON and its shard into the app bundle; never fall back to a CDN.
    path: `${import.meta.env.BASE_URL}models/model.json`,
    packageInformation: undefined,
  });
  return new Upscaler({ model: localModel }) as UpscalerEngine;
}

/** Runs the bundled ESRGAN-Slim 2x model locally in TensorFlow.js. */
export async function runEsrganUpscale(
  image: HTMLImageElement,
  onProgress: (progress: number) => void,
  onStatus: (status: string) => void,
) {
  onStatus('Loading the local AI engine…');
  if (!enginePromise) enginePromise = createEngine();
  try {
    const engine = await enginePromise;
    onStatus('Running ESRGAN-Slim · 2×');
    let lastProgress = -1;
    const output = await engine.upscale(image, {
      output: 'base64',
      patchSize: 64,
      padding: 8,
      progress: (amount) => {
        const percent = Math.max(0, Math.min(100, Math.round(amount * 100)));
        if (percent !== lastProgress) { lastProgress = percent; onProgress(percent); }
      },
    });
    if (!output.startsWith('data:image/')) throw new Error('The AI model returned an unsupported image format.');
    return output;
  } catch (error) {
    enginePromise = null;
    throw error;
  }
}
