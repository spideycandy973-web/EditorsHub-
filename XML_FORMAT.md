# EditorsHub XML project format

Export: **Project → Export XML**. Import: **Project → Import XML** (paste, choose a file, or
load the built-in example). Both full projects and layer/preset fragments are accepted.

## Full schema

```xml
<?xml version="1.0" encoding="UTF-8"?>
<EditorsHubMotion version="1.0" name="My Edit" width="1080" height="1920"
                  fps="30" duration="8" background="#0b0c14">
  <Assets>
    <Asset id="a1" name="clip.mp4" type="video" mime="video/mp4"
           width="1920" height="1080" duration="6.5"
           src="data:video/mp4;base64,..." fontFamily=""/>
  </Assets>
  <Layers>                         <!-- index 0 = TOPMOST layer -->
    <Layer id="l1" name="Headline" type="text" start="0.2" duration="5"
           inPoint="0" visible="1" locked="0" blend="source-over"
           parentId="" sourceId=""
           text="HELLO" fontFamily="Arial, Helvetica, sans-serif" fontWeight="800"
           italic="0" align="center" uppercase="0"
           fillType="linear" color="#ffffff" color2="#a78bfa"
           strokeColor="#000000" shape="rectangle" fit="cover"
           audio="0" loop="1">
      <Properties>
        <Property name="x" value="0" />
        <Property name="opacity" value="100">
          <Keyframe time="0"   value="0"   easing="easeOut" />
          <Keyframe time="0.8" value="100" easing="linear" />
        </Property>
        <Property name="scaleX" value="100" keyframes="0:70;1:100" />
      </Properties>
      <Effects>
        <Effect type="glow" enabled="1">
          <Param name="radius" value="40">
            <Keyframe time="0" value="0" easing="easeOut" />
            <Keyframe time="1" value="40" easing="linear" />
          </Param>
          <Param name="hue" value="280" />
        </Effect>
      </Effects>
      <Mask type="ellipse" feather="20" invert="0" />
    </Layer>
  </Layers>
</EditorsHubMotion>
```

### Root attributes
`name`, `width`, `height`, `fps`, `duration`, `background`.
Aliases: `Project`, `AlightMotion`, `AMProject`, `MotionProject`, `Composition`, `Comp`;
`frameRate`, `resolutionX/Y`, `backgroundColor`, `totalTime`.

### Assets
`src` may be a `data:` URI (embedded media is restored on import, including fonts) or a
path (reported as a warning — re-import that file in the Add panel). `type` is
`image | video | audio | font`. Exports embed files up to 12 MB when "Embed media in XML" is on.

### Layer attributes
`id`, `name`, `type`, `start`, `duration`, `inPoint`, `visible`, `locked`, `blend`,
`parentId`, `sourceId`.
Type aliases: `solid/background`, `shape/vector`, `text/title/caption`, `image/photo`,
`video/movie/clip`, `audio/sound`, `group/null/folder`.
Timing aliases: `startTime`, `length`, `trimStart`, `sourceOffset`.
Blend aliases: `normal` → `source-over`, `add` → `lighter`.

All string/enum style attributes can also appear inside `<Style …/>`, `<Text>`, `<Shape>`
or `<Content>` child elements. Common aliases: `fillColor`, `primaryColor`,
`secondaryColor`, `gradientColor`, `font`, `fontName`, `textAlign`, `fill`, `fitMode`.

### Properties / keyframes
Numeric values are project pixels, degrees, or percentages as labelled in the app.
Keyframe times are **layer-local seconds** (0 = layer start); the timeline displays them at
`start + time`. Easing values: `linear`, `hold`, `easeIn`, `easeOut`, `easeInOut`,
`easeInBack`, `easeOutBack`, `elastic`, `bounce`, `spring`. Easing applies to the segment
*leaving* that keyframe.

Accepted property syntaxes:
1. `<Property name="x" value="120" />` — static.
2. `<Property name="x" value="120"><Keyframe time="0" value="0" easing="easeOut"/>…</Property>`
3. `<Property name="x" keyframes="0:0;1:120" />` — inline list (`time:value[:easing]`).
4. `<Position x="100" y="-40" />`, `<Scale x="100" y="100" />`, `<Anchor x="0" y="0" />`
5. Attributes directly on the layer: `<Layer x="100" rotation="45" opacity="80" …/>`
6. `<Keyframes><Keyframe …/></Keyframes>`, `<Key>`, `<Kf>`, `<Point>`, or `<Frame>`
   (`frame` values are divided by `fps`).

Property name aliases are normalized (case/space/`_`/`-` insensitive):
`posX/translationX`, `scale_x`, `alpha`, `angle`, `fontsize`, `tracking`, `rate`, etc.

### Effects
`<Effect type="…" enabled="1">` with `<Param name="…" value="…">` children (keyframes work
exactly like properties). Effect type aliases include `gaussianBlur`, `brightness`,
`colorCorrection`, `dropShadow`, `mosaic`, `grain`, `rgbsplit`, `chromaKey`/`greenScreen`,
`cameraShake`, `beat`. Unknown effect names are reported as warnings and skipped.

Available types: `blur`, `sharpen`, `zoomBlur`, `colorAdjust`, `grayscale`, `sepia`,
`invert`, `tint`, `glow`, `dropShadow`, `outline`, `pixelate`, `noise`, `vignette`,
`chromatic`, `chromaKey`, `wave`, `shake`, `pulse`.

### Masks
`<Mask type="none|rect|ellipse|linear" feather="0" invert="0" />`.

## Preset fragments

A file whose root is a single `<Layer>` (or any element containing layer-ish children) is
treated as a **preset**: no `<EditorsHubMotion>` root, no project settings needed. The
import dialog then offers:

- **Open as project** — creates a new composition containing the parsed layers.
- **Add layers to current** — appends them to the open project (IDs are regenerated,
  parent links remapped, media re-linked).

## Alight Motion XML notes

Alight Motion XML presets are widely shared project "blueprints". Their exact schema is
proprietary and can reference app-specific effects and external media. This importer
reconstructs the portable parts — layers, timing, transforms, keyframes, text, colors,
shapes and common effects — and shows a warning for anything it cannot map. Media referenced
only by filename must be re-imported; embedded `data:` URIs are restored automatically.
