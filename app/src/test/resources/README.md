# Canonical geometry test fixture

`canonical_face_points.txt` contains the 468 vertex positions from Google's
[MediaPipe canonical_face_model.obj](https://github.com/google-ai-edge/mediapipe/blob/master/mediapipe/modules/face_geometry/data/canonical_face_model.obj).
Downloaded 2026-11-09; only `v` records were extracted, in their original order.
This is a generic mesh, not a customer's image or measured head.
MediaPipe is licensed under Apache 2.0; see `MEDIAPIPE-LICENSE.txt`.
The fixture is used only by JVM tests and is not included in the application APK.

`public_portrait_mesh.txt` is MediaPipe output and a generated curve for the public
Grace Hopper photo in `src/androidTest/assets` (see that directory's attribution).
Generated on Samsung SM-A505F on 2026-11-09; it is not customer data. The header is
width, height, mesh count; subsequent lines contain mesh xyz followed by Bezier
nodes (x, y, inX, inY, outX, outY). It covers a hairline above the mesh boundary.
