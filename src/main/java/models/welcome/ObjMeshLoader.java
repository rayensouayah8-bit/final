package models.welcome;

import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ObjMeshLoader {
    public record ObjMeshStats(int pointCount, int faceCount, int texCoordCount) {}

    private ObjMeshLoader() {
    }

    public static MeshView loadMeshView(URL resource) {
        if (resource == null) {
            throw new IllegalArgumentException("OBJ resource URL cannot be null.");
        }

        List<Float> points = new ArrayList<>();
        List<Integer> faces = new ArrayList<>();

        try (InputStream is = resource.openStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (trimmed.startsWith("v ")) {
                    String[] tokens = trimmed.split("\\s+");
                    points.add(Float.parseFloat(tokens[1]));
                    points.add(Float.parseFloat(tokens[2]));
                    points.add(Float.parseFloat(tokens[3]));
                } else if (trimmed.startsWith("f ")) {
                    String[] tokens = trimmed.split("\\s+");
                    int[] vertexIndices = new int[tokens.length - 1];
                    for (int i = 1; i < tokens.length; i++) {
                        String[] parts = tokens[i].split("/");
                        int idx = Integer.parseInt(parts[0]);
                        vertexIndices[i - 1] = idx > 0 ? idx - 1 : idx;
                    }
                    triangulateFace(vertexIndices, faces);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed reading OBJ: " + resource, e);
        }

        TriangleMesh mesh = new TriangleMesh();
        float[] pointArray = new float[points.size()];
        for (int i = 0; i < points.size(); i++) {
            pointArray[i] = points.get(i);
        }
        mesh.getPoints().addAll(pointArray);
        mesh.getTexCoords().addAll(0f, 0f);

        int[] faceArray = new int[faces.size() * 2];
        for (int i = 0; i < faces.size(); i++) {
            faceArray[i * 2] = faces.get(i);
            faceArray[i * 2 + 1] = 0;
        }
        mesh.getFaces().addAll(faceArray);

        ObjMeshStats stats = new ObjMeshStats(
                mesh.getPoints().size() / 3,
                mesh.getFaces().size() / 6,
                mesh.getTexCoords().size() / 2
        );
        System.out.println("[OBJ-DEBUG] resource=" + resource);
        System.out.println("[OBJ-DEBUG] points=" + stats.pointCount()
                + ", faces=" + stats.faceCount()
                + ", texCoords=" + stats.texCoordCount());

        return new MeshView(mesh);
    }

    private static void triangulateFace(int[] indices, List<Integer> faces) {
        if (indices.length < 3) {
            return;
        }
        for (int i = 1; i < indices.length - 1; i++) {
            faces.add(indices[0]);
            faces.add(indices[i]);
            faces.add(indices[i + 1]);
        }
    }
}
