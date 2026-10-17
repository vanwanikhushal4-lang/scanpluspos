# ScanPlus logo asset

The Figma design was inspected read-only. No layers, properties, export settings, or other design data were changed. The browser's guest mode required sign-in to export, so the attached screenshot was used for this local asset.

- `public/brand/scanplus-logo.png`: transparent cutout prepared with the built-in image editing tool. This is a raster preparation from the attachment, not an SVG export from Figma.
- `design/scanplus-logo-reference.png`: unchanged supplied screenshot.
- Source Figma frame: https://www.figma.com/design/g5Tru5PI83ZEtbwtmA75Jh/Dashboard?node-id=6894-14754
- React applies a display crop to transparent margins. The logo now sits directly on the dark sidebar with no background panel or rounded card. A second clipped copy uses a CSS filter to lighten only the subtitle; the original red/cyan wordmark, shield, and PNG remain unchanged. Narrow desktop navigation uses a crop of the shield; mobile and expanded navigation show the complete lockup.

## Image editing prompt

Use case: background-extraction. Edit target: the attached ScanPlus logo screenshot. Create a faithful clean cutout of the EXISTING logo for a React dashboard, not a new design. Remove only the flat dark gray Figma canvas, the gray text 'Frame 1261154588' above the logo, and the excess empty margins. Preserve exactly the metallic silver and black shield, its cyan/lime plus sign, the red serif 'Scan' and cyan serif 'Plus', the registered mark, and the black subtitle 'Unified Terminal Security & Management'. Keep the original proportions, typography and layout. Tight horizontal composition with minimal transparent padding. All the background, including spaces within letters, must have actual alpha transparency. Do not add, redesign, recolor, or spell anything differently. Save the resulting local PNG file and provide its filesystem path for integration.
