/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cientistavuador.softwarerenderer;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import cientistavuador.softwarerenderer.camera.FreeCamera;
import cientistavuador.softwarerenderer.render.SoftwareRenderer.Light;
import cientistavuador.softwarerenderer.render.SoftwareRenderer.PointLight;
import cientistavuador.softwarerenderer.render.SoftwareRenderer;
import cientistavuador.softwarerenderer.render.SoftwareRenderer.SpotLight;
import cientistavuador.softwarerenderer.render.SoftwareRenderer.Surface;
import cientistavuador.softwarerenderer.render.SoftwareRenderer.Texture;
import cientistavuador.softwarerenderer.resources.ImageResources;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;

/**
 *
 * @author cientista
 */
public class Game {

    private static final Game GAME = new Game();

    public static Game get() {
        return GAME;
    }

    private final Font BIG_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 26);
    private final Font SMALL_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 14);
    private boolean textEnabled = true;
    private final FreeCamera camera = new FreeCamera();

    private final SoftwareRenderer renderer = new SoftwareRenderer(400, 300);
    private float rotation = 0f;

    private Throwable imageThreadException = null;
    private final Exchanger<Object> imageThreadExchanger = new Exchanger<>();
    private final Thread imageThread = new Thread(() -> {
        try {
            Surface surface = (Surface) this.imageThreadExchanger.exchange(null);
            while (true) {
                if (surface == null) {
                    surface = (Surface) this.imageThreadExchanger.exchange(null);
                    continue;
                }
                BufferedImage result = SoftwareRenderer.textureToImage(surface.getColorBufferTexture());
                surface = (Surface) this.imageThreadExchanger.exchange(result);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }, "Image-Thread");

    private final float[] cottageVertices;
    private final Texture cottageTexture;
    private final Matrix4f cottageMatrix = new Matrix4f()
            .translate(82.61f, 59.5f, -30.05f)
            .scale(0.5f)
            .rotateY((float) Math.toRadians(45f));

    private final float[] terrainVertices;
    private final Texture terrainTexture;
    private final Matrix4f terrainMatrix = new Matrix4f()
            .scale(512f);

    private final float[] colaVertices;
    private final Texture colaTexture;
    private final Matrix4f colaMatrix = new Matrix4f();

    private final float[] lightIconVertices;
    private final Texture pointLightIcon;
    private final Texture spotLightIcon;
    private final Texture lightColorIcon;

    private boolean lightingEnabled = true;
    private boolean terrainEnabled = true;

    private final SpotLight colaLight = new SpotLight();
    private final PointLight doorLight = new PointLight();

    private final SpotLight flashlight = new SpotLight();
    private boolean flashlightEnabled = false;

    private boolean saveColorBuffer = false;
    private boolean saveDepthBuffer = false;

    private final SoftwareRenderer offscreenRenderer = new SoftwareRenderer(75, 100);

    private Game() {
        //load 3d model, texture and model matrix
        this.cottageVertices = loadModel("cottage.obj");
        this.terrainVertices = loadModel("terrain.obj");
        this.colaVertices = loadModel("ciencola.obj");
        this.lightIconVertices = loadModel("billboard.obj");
        this.cottageTexture = SoftwareRenderer.wrapImageToTexture(ImageResources.read("cottage_diffuse.png"));
        this.terrainTexture = SoftwareRenderer.wrapImageToTexture(ImageResources.read("grass09.png"));
        this.colaTexture = SoftwareRenderer.wrapImageToTexture(ImageResources.read("ciencola_diffuse.png"));
        this.pointLightIcon = SoftwareRenderer.wrapImageToTexture(ImageResources.read("pointlight.png"));
        this.spotLightIcon = SoftwareRenderer.wrapImageToTexture(ImageResources.read("spotlight.png"));
        this.lightColorIcon = SoftwareRenderer.wrapImageToTexture(ImageResources.read("lightcolor.png"));
    }

    private float[] loadModel(String name) {
        try (BufferedReader reader
                = new BufferedReader(
                        new InputStreamReader(
                                ImageResources.class.getResourceAsStream(name),
                                StandardCharsets.UTF_8
                        )
                )) {

            this.renderer.beginMesh();

            String s;
            while ((s = reader.readLine()) != null) {
                if (s.startsWith("#") || s.isBlank()) {
                    continue;
                }
                String[] split = s.split(" ");

                switch (split[0]) {
                    case "v" -> {
                        float x = Float.parseFloat(split[1]);
                        float y = Float.parseFloat(split[2]);
                        float z = Float.parseFloat(split[3]);
                        this.renderer.position(x, y, z);
                    }
                    case "vt" -> {
                        float u = Float.parseFloat(split[1]);
                        float v = Float.parseFloat(split[2]);
                        this.renderer.texture(u, v);
                    }
                    case "vn" -> {
                        float nx = Float.parseFloat(split[1]);
                        float ny = Float.parseFloat(split[2]);
                        float nz = Float.parseFloat(split[3]);
                        this.renderer.normal(nx, ny, nz);
                    }
                    case "f" -> {
                        for (int i = 0; i < 3; i++) {
                            String[] faceSplit = split[1 + i].split("/");
                            int position = Integer.parseInt(faceSplit[0]);
                            int uv = Integer.parseInt(faceSplit[1]);
                            int normal = Integer.parseInt(faceSplit[2]);
                            this.renderer.vertex(position, uv, normal, 0);
                        }
                    }
                }

            }

            return this.renderer.finishMesh();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void start() {
        this.renderer.getClearColor().set(0.2f, 0.4f, 0.6f, 1f);

        camera.setPosition(103.17f, 68.80f, -21.42f);
        camera.setRotation(0f, -180f, 0f);
        Thread currentThread = Thread.currentThread();
        this.imageThread.setUncaughtExceptionHandler((t, e) -> {
            this.imageThreadException = e;
            currentThread.interrupt();
        });
        this.imageThread.setDaemon(true);
        this.imageThread.start();
        this.renderer.setBilinearFilteringEnabled(false);

        this.renderer.getSunDiffuse().set(0.7f, 0.65f, 0.60f);

        this.colaLight.getDiffuseColor().set(4f, 0.5f, 0.0f);
        this.colaLight.getAmbientColor().set(this.colaLight.getDiffuseColor()).mul(0.05f);
        this.colaLight.getPosition().set(83.70f, 65f, -6.82f);
        this.renderer.getLights().add(this.colaLight);

        this.doorLight.getDiffuseColor().set(0.0f, 2f, 0.5f);
        this.doorLight.getAmbientColor().set(this.doorLight.getDiffuseColor()).mul(0.05f);
        this.doorLight.getPosition().set(75.64f, 64.56f, -22.29f);
        this.renderer.getLights().add(this.doorLight);

        this.flashlight.getDiffuseColor().set(1.25f);
        this.flashlight.getAmbientColor().set(this.flashlight.getDiffuseColor()).mul(0.2f);
    }

    public void loop(Graphics2D g) {
        if (this.imageThreadException != null) {
            throw new RuntimeException("Exception in Image Thread", this.imageThreadException);
        }

        //offscreen rendering
        Future<Texture> offscreenResult = CompletableFuture.supplyAsync(() -> {
            this.offscreenRenderer.getClearColor().set(0.1f, 0.1f, 0.1f, 0.5f);
            this.offscreenRenderer.clearBuffers();
            this.offscreenRenderer.setMesh(this.colaVertices);
            this.offscreenRenderer.setTexture(this.colaTexture);
            this.offscreenRenderer.getProjection().identity().perspective(90f, 800f / 600f, 0.01f, 100f);
            this.offscreenRenderer.getView().identity();
            this.offscreenRenderer.getCameraPosition().set(0f, 0f, 1.1f);
            this.offscreenRenderer.getModel()
                    .identity()
                    .rotateY((float) Math.toRadians(this.rotation))
                    .rotateX((float) Math.toRadians(25f));
            this.offscreenRenderer.render();
            return this.offscreenRenderer.colorBuffer();
        });

        this.colaMatrix
                .identity()
                .translate(83.70f, 62f + Math.abs((this.rotation / 720f) - 0.25f), -6.82f)
                .rotateY((float) Math.toRadians(this.rotation))
                .rotateX((float) Math.toRadians(25f));
        this.rotation += Main.TPF * 30f;
        if (this.rotation > 360f) {
            this.rotation = 0f;
        }

        camera.updateMovement();

        this.flashlight.getPosition().set(this.camera.getPosition());
        this.flashlight.getDirection().set(this.camera.getFront());

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 800, 600);

        this.renderer.clearBuffers();

        this.renderer.getProjection().set(this.camera.getProjection());
        this.renderer.getView().set(this.camera.getView());
        this.renderer.getCameraPosition().set(this.camera.getPosition());

        this.renderer.setLightingEnabled(this.lightingEnabled);

        int renderedVertices = 0;

        //terrain
        if (this.terrainEnabled) {
            this.renderer.setMesh(this.terrainVertices);
            this.renderer.getModel().set(this.terrainMatrix);
            this.renderer.setTexture(this.terrainTexture);

            renderedVertices = this.renderer.render();

            Main.NUMBER_OF_VERTICES += renderedVertices;
            Main.NUMBER_OF_DRAWCALLS++;
        }

        //cottage
        this.renderer.setMesh(this.cottageVertices);
        this.renderer.getModel().set(this.cottageMatrix);
        this.renderer.setTexture(this.cottageTexture);

        renderedVertices = this.renderer.render();

        Main.NUMBER_OF_VERTICES += renderedVertices;
        Main.NUMBER_OF_DRAWCALLS++;

        //cola
        this.renderer.setMesh(this.colaVertices);
        this.renderer.getModel().set(this.colaMatrix);
        this.renderer.setTexture(this.colaTexture);

        renderedVertices = this.renderer.render();

        Main.NUMBER_OF_VERTICES += renderedVertices;
        Main.NUMBER_OF_DRAWCALLS++;

        if (this.lightingEnabled) {
            //lights
            this.renderer.setMesh(this.lightIconVertices);
            this.renderer.setBillboardingEnabled(true);
            this.renderer.setLightingEnabled(false);
            for (Light light : this.renderer.getLights()) {
                this.renderer.getModel().identity().translate(light.getPosition());

                //icon
                if (light instanceof SpotLight) {
                    this.renderer.setTexture(this.spotLightIcon);
                } else {
                    this.renderer.setTexture(this.pointLightIcon);
                }
                renderedVertices = this.renderer.render();

                //overlay
                float lightR = light.getDiffuseColor().x();
                float lightG = light.getDiffuseColor().y();
                float lightB = light.getDiffuseColor().z();
                float largest = Math.max(Math.max(lightR, lightG), lightB);
                if (largest > 1f) {
                    lightR /= largest;
                    lightG /= largest;
                    lightB /= largest;
                }
                this.renderer.getColor().set(lightR, lightG, lightB, 1f);
                this.renderer.setTexture(this.lightColorIcon);
                renderedVertices += this.renderer.render();

                Main.NUMBER_OF_VERTICES += renderedVertices;
                Main.NUMBER_OF_DRAWCALLS += 2;

                this.renderer.getColor().set(1f);
            }
            this.renderer.setBillboardingEnabled(false);
            this.renderer.setLightingEnabled(true);
        }

        //render offscreen renderer quad
        this.renderer.setMesh(this.lightIconVertices);
        this.renderer.setBillboardingEnabled(true);
        this.renderer.setLightingEnabled(false);
        this.renderer
                .getModel()
                .identity()
                .translate(90.17f, 61.80f, -21.42f)
                .scale(4f);
        try {
            this.renderer.setTexture(offscreenResult.get());
        } catch (InterruptedException | ExecutionException ex) {
            throw new RuntimeException(ex);
        }
        Main.NUMBER_OF_VERTICES += this.renderer.render();
        Main.NUMBER_OF_DRAWCALLS++;
        this.renderer.setBillboardingEnabled(false);
        this.renderer.setLightingEnabled(true);

        if (this.saveColorBuffer) {
            this.saveColorBuffer = false;
            try {
                ImageIO.write(this.renderer.colorBufferToImage(), "PNG", new File("color_buffer.png"));
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        if (this.saveDepthBuffer) {
            this.saveDepthBuffer = false;
            try {
                ImageIO.write(this.renderer.depthBufferToImage(), "PNG", new File("depth_buffer.png"));
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        try {
            BufferedImage e = (BufferedImage) this.imageThreadExchanger.exchange(this.renderer.getSurface());

            this.renderer.flipSurfaces();

            g.drawImage(e, 0, 0, Main.WIDTH, Main.HEIGHT, null);
        } catch (InterruptedException ex) {
            if (this.imageThreadException != null) {
                throw new RuntimeException("Exception in Image Thread", this.imageThreadException);
            } else {
                throw new RuntimeException(ex);
            }
        }

        if (this.textEnabled) {
            g.setFont(BIG_FONT);
            g.setColor(Color.YELLOW);
            g.drawString("SoftwareRenderer", 0, BIG_FONT.getSize());

            String[] wallOfText = {
                "FPS: " + Main.FPS,
                "X: " + format(camera.getPosition().x()),
                "Y: " + format(camera.getPosition().y()),
                "Z: " + format(camera.getPosition().z()),
                "Controls:",
                "  WASD + Space + Mouse - Move",
                "  Shift - Run",
                "  Alt - Wander",
                "  Ctrl - Unlock/Lock mouse",
                "  T - Hide This Wall of Text.",
                "  M - Multithread [" + (this.renderer.isMultithreadEnabled() ? "Enabled" : "Disabled") + "]",
                "  B - Bilinear Filtering [" + (this.renderer.isBilinearFilteringEnabled() ? "Enabled" : "Disabled") + "]",
                "  R - Resolution [" + this.renderer.getWidth() + "x" + this.renderer.getHeight() + "]",
                "  L - Lighting [" + (this.lightingEnabled ? "Enabled" : "Disabled") + "]",
                "  N - Terrain [" + (this.terrainEnabled ? "Enabled" : "Disabled") + "]",
                "  F - Flashlight [" + (this.flashlightEnabled ? "Enabled" : "Disabled") + "]",
                "  U - Sun [" + (this.renderer.isSunEnabled() ? "Enabled" : "Disabled") + "]",
                "  C - Save Color Buffer to 'color_buffer.png'",
                "  P - Save Depth Buffer to 'depth_buffer.png'"
            };

            int offset = SMALL_FONT.getSize();
            int offsetBig = BIG_FONT.getSize();
            g.setFont(SMALL_FONT);
            g.setColor(Color.WHITE);
            for (int i = 0; i < wallOfText.length; i++) {
                g.drawString(wallOfText[i], 0, (offset * i) + (offsetBig * 2));
            }
        }

        Main.WINDOW_TITLE += " (DrawCalls: " + Main.NUMBER_OF_DRAWCALLS + ", Vertices: " + Main.NUMBER_OF_VERTICES + ")";
        Main.WINDOW_TITLE += " (x:" + (int) Math.floor(camera.getPosition().x()) + ",y:" + (int) Math.floor(camera.getPosition().y()) + ",z:" + (int) Math.ceil(camera.getPosition().z()) + ")";
        if (!this.textEnabled) {
            Main.WINDOW_TITLE += " (T - Show Wall of Text)";
        }
    }

    private String format(double d) {
        return String.format("%.2f", d);
    }

    public void keyCallback(KeyEvent e, boolean pressed) {
        if (e.getKeyCode() == KeyEvent.VK_T && pressed) {
            this.textEnabled = !this.textEnabled;
        }
        if (e.getKeyCode() == KeyEvent.VK_M && pressed) {
            this.renderer.setMultithreadEnabled(!this.renderer.isMultithreadEnabled());
        }
        if (e.getKeyCode() == KeyEvent.VK_B && pressed) {
            this.renderer.setBilinearFilteringEnabled(!this.renderer.isBilinearFilteringEnabled());
        }
        if (e.getKeyCode() == KeyEvent.VK_R && pressed) {
            try {
                this.imageThreadExchanger.exchange(null);
            } catch (InterruptedException ex) {
                if (this.imageThreadException != null) {
                    throw new RuntimeException("Exception in Image Thread", this.imageThreadException);
                } else {
                    throw new RuntimeException(ex);
                }
            }
            switch (this.renderer.getWidth()) {
                case 100 -> {
                    this.renderer.resize(200, 150);
                }
                case 200 -> {
                    this.renderer.resize(300, 225);
                }
                case 300 -> {
                    this.renderer.resize(400, 300);
                }
                case 400 -> {
                    this.renderer.resize(500, 375);
                }
                case 500 -> {
                    this.renderer.resize(600, 450);
                }
                case 600 -> {
                    this.renderer.resize(700, 525);
                }
                case 700 -> {
                    this.renderer.resize(800, 600);
                }
                case 800 -> {
                    this.renderer.resize(100, 75);
                }
            }
        }
        if (e.getKeyCode() == KeyEvent.VK_L && pressed) {
            this.lightingEnabled = !this.lightingEnabled;
        }
        if (e.getKeyCode() == KeyEvent.VK_N && pressed) {
            this.terrainEnabled = !this.terrainEnabled;
        }
        if (e.getKeyCode() == KeyEvent.VK_F && pressed) {
            this.flashlightEnabled = !this.flashlightEnabled;

            if (this.flashlightEnabled) {
                this.renderer.getLights().add(this.flashlight);
            } else {
                this.renderer.getLights().remove(this.flashlight);
            }
        }
        if (e.getKeyCode() == KeyEvent.VK_U && pressed) {
            this.renderer.setSunEnabled(!this.renderer.isSunEnabled());
        }
        if (e.getKeyCode() == KeyEvent.VK_C && pressed) {
            this.saveColorBuffer = true;
        }
        if (e.getKeyCode() == KeyEvent.VK_P && pressed) {
            this.saveDepthBuffer = true;
        }
    }

    public void mouseCursorMoved(double x, double y) {
        camera.mouseCursorMoved(x, y);
    }

}
