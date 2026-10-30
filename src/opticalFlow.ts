export type BlockFlow = {
  columns: number;
  rows: number;
  blockSize: number;
  width: number;
  height: number;
  dx: Float32Array;
  dy: Float32Array;
  confidence: Float32Array;
};

function luminance(data: Uint8ClampedArray, width: number, x: number, y: number) {
  const index = (y * width + x) * 4;
  return data[index] * .299 + data[index + 1] * .587 + data[index + 2] * .114;
}

/**
 * Coarse block-matching optical flow for low-resolution, on-device interpolation.
 * It intentionally trades fine motion boundaries for predictable mobile CPU use.
 */
export function estimateBlockFlow(previous: ImageData, current: ImageData, blockSize = 8, searchRadius = 4): BlockFlow {
  const width = Math.min(previous.width, current.width);
  const height = Math.min(previous.height, current.height);
  const columns = Math.ceil(width / blockSize);
  const rows = Math.ceil(height / blockSize);
  const dx = new Float32Array(columns * rows);
  const dy = new Float32Array(columns * rows);
  const confidence = new Float32Array(columns * rows);
  const patchStep = 2;
  const maxMotion = Math.min(searchRadius, Math.max(1, Math.floor(Math.min(width, height) / 16)));

  for (let row = 0; row < rows; row++) {
    for (let column = 0; column < columns; column++) {
      const left = column * blockSize;
      const top = row * blockSize;
      const right = Math.min(width - 1, left + blockSize - 1);
      const bottom = Math.min(height - 1, top + blockSize - 1);
      let bestCost = Number.POSITIVE_INFINITY;
      let secondBest = Number.POSITIVE_INFINITY;
      let bestX = 0;
      let bestY = 0;

      for (let offsetY = -maxMotion; offsetY <= maxMotion; offsetY++) {
        for (let offsetX = -maxMotion; offsetX <= maxMotion; offsetX++) {
          if (left + offsetX < 0 || top + offsetY < 0 || right + offsetX >= width || bottom + offsetY >= height) continue;
          let cost = 0;
          let samples = 0;
          for (let y = top; y <= bottom; y += patchStep) {
            for (let x = left; x <= right; x += patchStep) {
              cost += Math.abs(luminance(previous.data, width, x, y) - luminance(current.data, width, x + offsetX, y + offsetY));
              samples++;
            }
          }
          cost /= Math.max(1, samples);
          if (cost < bestCost) {
            secondBest = bestCost;
            bestCost = cost;
            bestX = offsetX;
            bestY = offsetY;
          } else if (cost < secondBest) secondBest = cost;
        }
      }

      const index = row * columns + column;
      dx[index] = bestX;
      dy[index] = bestY;
      const ambiguity = secondBest > 0 ? Math.max(0, Math.min(1, (secondBest - bestCost) / secondBest)) : 0;
      const texture = Math.min(1, Math.max(0, (secondBest - bestCost) / 12));
      confidence[index] = Math.max(.15, Math.min(1, Math.max(ambiguity, texture)));
    }
  }

  return { columns, rows, blockSize, width, height, dx, dy, confidence };
}

function flowAt(flow: BlockFlow, x: number, y: number) {
  const gridX = Math.max(0, Math.min(flow.columns - 1, (x - flow.blockSize / 2) / flow.blockSize));
  const gridY = Math.max(0, Math.min(flow.rows - 1, (y - flow.blockSize / 2) / flow.blockSize));
  const x0 = Math.floor(gridX); const y0 = Math.floor(gridY);
  const x1 = Math.min(flow.columns - 1, x0 + 1); const y1 = Math.min(flow.rows - 1, y0 + 1);
  const tx = gridX - x0; const ty = gridY - y0;
  const blend = (values: Float32Array) => {
    const top = values[y0 * flow.columns + x0] * (1 - tx) + values[y0 * flow.columns + x1] * tx;
    const bottom = values[y1 * flow.columns + x0] * (1 - tx) + values[y1 * flow.columns + x1] * tx;
    return top * (1 - ty) + bottom * ty;
  };
  return { dx: blend(flow.dx), dy: blend(flow.dy), confidence: blend(flow.confidence) };
}

function sampleChannel(data: Uint8ClampedArray, width: number, height: number, x: number, y: number, channel: number) {
  const safeX = Math.max(0, Math.min(width - 1, x));
  const safeY = Math.max(0, Math.min(height - 1, y));
  const x0 = Math.floor(safeX); const y0 = Math.floor(safeY);
  const x1 = Math.min(width - 1, x0 + 1); const y1 = Math.min(height - 1, y0 + 1);
  const tx = safeX - x0; const ty = safeY - y0;
  const top = data[(y0 * width + x0) * 4 + channel] * (1 - tx) + data[(y0 * width + x1) * 4 + channel] * tx;
  const bottom = data[(y1 * width + x0) * 4 + channel] * (1 - tx) + data[(y1 * width + x1) * 4 + channel] * tx;
  return top * (1 - ty) + bottom * ty;
}

/** Predicts the next fractional source frame by warping the latest frame along its measured motion. */
export function extrapolateMotionFrame(current: ImageData, flow: BlockFlow, amount: number): ImageData {
  const width = current.width;
  const height = current.height;
  const progress = Math.max(0, Math.min(1, amount));
  const output = new ImageData(width, height);
  const pixels = output.data;
  const input = current.data;

  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const { dx, dy, confidence } = flowAt(flow, x, y);
      const motionScale = progress * confidence;
      const sourceX = x - dx * motionScale;
      const sourceY = y - dy * motionScale;
      const outputIndex = (y * width + x) * 4;
      for (let channel = 0; channel < 3; channel++) {
        const warped = sampleChannel(input, width, height, sourceX, sourceY, channel);
        const original = input[outputIndex + channel];
        pixels[outputIndex + channel] = original * (1 - confidence) + warped * confidence;
      }
      pixels[outputIndex + 3] = 255;
    }
  }
  return output;
}

export function fingerprintFrame(frame: ImageData): number {
  let hash = 2166136261;
  const horizontalStep = Math.max(1, Math.floor(frame.width / 36));
  const verticalStep = Math.max(1, Math.floor(frame.height / 20));
  for (let y = 0; y < frame.height; y += verticalStep) {
    for (let x = 0; x < frame.width; x += horizontalStep) {
      const index = (y * frame.width + x) * 4;
      hash = Math.imul(hash ^ frame.data[index], 16777619);
      hash = Math.imul(hash ^ frame.data[index + 1], 16777619);
      hash = Math.imul(hash ^ frame.data[index + 2], 16777619);
    }
  }
  return hash >>> 0;
}

export function flowToImageData(flow: BlockFlow): ImageData {
  const image = new ImageData(flow.columns, flow.rows);
  for (let index = 0; index < flow.dx.length; index++) {
    const offset = index * 4;
    image.data[offset] = Math.max(0, Math.min(255, Math.round(128 + flow.dx[index] * 16)));
    image.data[offset + 1] = Math.max(0, Math.min(255, Math.round(128 + flow.dy[index] * 16)));
    image.data[offset + 2] = Math.max(0, Math.min(255, Math.round(flow.confidence[index] * 255)));
    image.data[offset + 3] = 255;
  }
  return image;
}

/** GPU warp that applies a low-resolution motion field to a full-resolution export frame. */
export class MotionWarpRenderer {
  readonly canvas: HTMLCanvasElement;
  private readonly gl: WebGLRenderingContext;
  private readonly program: WebGLProgram;
  private readonly currentTexture: WebGLTexture;
  private readonly flowTexture: WebGLTexture;
  private readonly analysisSize: WebGLUniformLocation;
  private readonly amountUniform: WebGLUniformLocation;
  private lastTextureKey = '';

  constructor(width: number, height: number) {
    this.canvas = document.createElement('canvas');
    this.canvas.width = width; this.canvas.height = height;
    const gl = this.canvas.getContext('webgl', { alpha: false, antialias: false, preserveDrawingBuffer: true, powerPreference: 'low-power' });
    if (!gl) throw new Error('WebGL motion warping is unavailable.');
    if (Math.max(width, height) > gl.getParameter(gl.MAX_TEXTURE_SIZE)) throw new Error('Render exceeds the device WebGL texture limit.');
    this.gl = gl;

    const vertexSource = 'attribute vec2 a_position; varying vec2 v_uv; void main(){ v_uv=(a_position+1.0)*0.5; gl_Position=vec4(a_position,0.0,1.0); }';
    const fragmentSource = 'precision mediump float; varying vec2 v_uv; uniform sampler2D u_current; uniform sampler2D u_flow; uniform vec2 u_analysisSize; uniform float u_amount; void main(){ vec4 flow=texture2D(u_flow,v_uv); vec2 delta=(flow.rg*255.0-vec2(128.0))/16.0; float confidence=flow.b; vec2 sampleUv=clamp(v_uv-(delta/u_analysisSize)*u_amount*confidence,vec2(0.0),vec2(1.0)); gl_FragColor=texture2D(u_current,sampleUv); }';
    const vertexShader = this.compileShader(gl.VERTEX_SHADER, vertexSource);
    const fragmentShader = this.compileShader(gl.FRAGMENT_SHADER, fragmentSource);
    const program = gl.createProgram();
    if (!program) throw new Error('Could not create the WebGL motion shader.');
    gl.attachShader(program, vertexShader); gl.attachShader(program, fragmentShader); gl.linkProgram(program);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(program) || 'Could not link the WebGL motion shader.');
    this.program = program;

    const position = gl.createBuffer();
    if (!position) throw new Error('Could not allocate the WebGL motion geometry.');
    gl.bindBuffer(gl.ARRAY_BUFFER, position);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
    gl.useProgram(program);
    const location = gl.getAttribLocation(program, 'a_position');
    gl.enableVertexAttribArray(location); gl.vertexAttribPointer(location, 2, gl.FLOAT, false, 0, 0);

    const currentTexture = gl.createTexture(); const flowTexture = gl.createTexture();
    if (!currentTexture || !flowTexture) throw new Error('Could not allocate WebGL motion textures.');
    this.currentTexture = currentTexture; this.flowTexture = flowTexture;
    for (const texture of [currentTexture, flowTexture]) {
      gl.bindTexture(gl.TEXTURE_2D, texture);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    }
    const currentUniform = gl.getUniformLocation(program, 'u_current');
    const flowUniform = gl.getUniformLocation(program, 'u_flow');
    const analysisSize = gl.getUniformLocation(program, 'u_analysisSize');
    const amountUniform = gl.getUniformLocation(program, 'u_amount');
    if (!currentUniform || !flowUniform || !analysisSize || !amountUniform) throw new Error('WebGL motion shader uniforms are unavailable.');
    this.analysisSize = analysisSize; this.amountUniform = amountUniform;
    gl.uniform1i(currentUniform, 0); gl.uniform1i(flowUniform, 1);
    gl.disable(gl.DEPTH_TEST); gl.disable(gl.BLEND);
  }

  private compileShader(type: number, source: string) {
    const shader = this.gl.createShader(type);
    if (!shader) throw new Error('Could not compile the WebGL motion shader.');
    this.gl.shaderSource(shader, source); this.gl.compileShader(shader);
    if (!this.gl.getShaderParameter(shader, this.gl.COMPILE_STATUS)) throw new Error(this.gl.getShaderInfoLog(shader) || 'Could not compile the WebGL motion shader.');
    return shader;
  }

  render(current: HTMLCanvasElement, flowMap: HTMLCanvasElement, analysisWidth: number, analysisHeight: number, amount: number, textureKey: string) {
    const gl = this.gl;
    gl.useProgram(this.program);
    gl.viewport(0, 0, this.canvas.width, this.canvas.height);
    if (textureKey !== this.lastTextureKey) {
      gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, 1);
      gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, this.currentTexture);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, current);
      gl.activeTexture(gl.TEXTURE1); gl.bindTexture(gl.TEXTURE_2D, this.flowTexture);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, flowMap);
      this.lastTextureKey = textureKey;
    }
    gl.uniform2f(this.analysisSize, analysisWidth, analysisHeight);
    gl.uniform1f(this.amountUniform, Math.max(0, Math.min(1, amount)));
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    return this.canvas;
  }
}
