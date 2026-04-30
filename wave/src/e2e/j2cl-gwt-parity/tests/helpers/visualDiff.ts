import type { Locator, TestInfo } from "@playwright/test";
import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import pixelmatch from "pixelmatch";
import { PNG } from "pngjs";

export type VisualDiffResult = {
  mismatchedPixels: number;
  totalPixels: number;
  mismatchRatio: number;
};

export async function compareLocatorScreenshots(
  testInfo: TestInfo,
  name: string,
  left: Locator,
  right: Locator
): Promise<VisualDiffResult> {
  const [leftShot, rightShot] = await Promise.all([
    left.screenshot(),
    right.screenshot()
  ]);

  await testInfo.attach(`${name}-left.png`, {
    body: leftShot,
    contentType: "image/png"
  });
  await testInfo.attach(`${name}-right.png`, {
    body: rightShot,
    contentType: "image/png"
  });
  writeDebugShot(name, "left", leftShot);
  writeDebugShot(name, "right", rightShot);

  const leftImage = PNG.sync.read(leftShot);
  const rightImage = PNG.sync.read(rightShot);
  const width = Math.max(leftImage.width, rightImage.width);
  const height = Math.max(leftImage.height, rightImage.height);
  const normalizedLeft = normalizeToCanvas(leftImage, width, height);
  const normalizedRight = normalizeToCanvas(rightImage, width, height);
  const diff = new PNG({ width, height });
  const mismatchedPixels = pixelmatch(
    normalizedLeft.data,
    normalizedRight.data,
    diff.data,
    width,
    height,
    { threshold: 0.12, includeAA: true }
  );
  const diffBuffer = PNG.sync.write(diff);
  await testInfo.attach(`${name}-diff.png`, {
    body: diffBuffer,
    contentType: "image/png"
  });
  writeDebugShot(name, "diff", diffBuffer);

  return {
    mismatchedPixels,
    totalPixels: width * height,
    mismatchRatio: width * height === 0 ? 1 : mismatchedPixels / (width * height)
  };
}

function writeDebugShot(name: string, side: string, buffer: Buffer): void {
  const dir = process.env.WAVE_E2E_VISUAL_DEBUG_DIR;
  if (!dir) return;
  try {
    mkdirSync(dir, { recursive: true });
    writeFileSync(join(dir, `${name}-${side}.png`), buffer);
  } catch {
    // Debug artifacts are optional diagnostics; comparison results must still report.
  }
}

function normalizeToCanvas(source: PNG, width = source.width, height = source.height): PNG {
  const canvas = new PNG({ width, height });
  fillWhite(canvas);
  PNG.bitblt(source, canvas, 0, 0, source.width, source.height, 0, 0);
  return canvas;
}

function fillWhite(image: PNG): void {
  for (let i = 0; i < image.data.length; i += 4) {
    image.data[i] = 255;
    image.data[i + 1] = 255;
    image.data[i + 2] = 255;
    image.data[i + 3] = 255;
  }
}
