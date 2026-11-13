package pl.holovis;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.Window;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Efekty pełnoekranowe na własnym, malutkim shaderze (czyste OpenGL, bez mixinów):
 *  - Motion Blur: mieszanie z poprzednią klatką (kolor "ciągnie się" za ruchem),
 *  - Kolorki: korekcja kolorów (nasycenie, kontrast, jasność, ciepło),
 *  - Rozmycie klienta: rozmycie gry za menu KosaVisuals (gaussian na 1/4 rozdzielczości).
 * Przed każdym przejściem zapisujemy stan OpenGL, a po nim go przywracamy, żeby nie psuć renderowania gry.
 * Gdy shader się nie skompiluje, efekty po prostu się wyłączają.
 */
public final class PostFx {
    private PostFx() {}

    private static final String VS = """
            #version 150
            out vec2 uv;
            void main() {
                vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                uv = p;
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    private static final String FS = """
            #version 150
            uniform sampler2D uTex;
            uniform sampler2D uPrev;
            uniform int uMode;      // 0 = mieszanie z poprzednią klatką, 1 = rozmycie, 2 = kopia, 3 = kolory
            uniform float uMix;
            uniform vec2 uStep;
            uniform vec4 uGrade;    // nasycenie, kontrast, jasność, ciepło
            in vec2 uv;
            out vec4 frag;
            void main() {
                if (uMode == 0) {
                    vec3 c = texture(uTex, uv).rgb;
                    vec3 p = texture(uPrev, uv).rgb;
                    frag = vec4(mix(c, p, uMix), 1.0);
                } else if (uMode == 1) {
                    vec3 c = texture(uTex, uv).rgb * 0.2270270270;
                    c += (texture(uTex, uv + uStep).rgb + texture(uTex, uv - uStep).rgb) * 0.1945945946;
                    c += (texture(uTex, uv + uStep * 2.0).rgb + texture(uTex, uv - uStep * 2.0).rgb) * 0.1216216216;
                    c += (texture(uTex, uv + uStep * 3.0).rgb + texture(uTex, uv - uStep * 3.0).rgb) * 0.0540540541;
                    c += (texture(uTex, uv + uStep * 4.0).rgb + texture(uTex, uv - uStep * 4.0).rgb) * 0.0162162162;
                    frag = vec4(c, 1.0);
                } else if (uMode == 2) {
                    frag = vec4(texture(uTex, uv).rgb, 1.0);
                } else {
                    vec3 c = texture(uTex, uv).rgb;
                    c = (c - 0.5) * uGrade.y + 0.5;
                    c += uGrade.z;
                    float l = dot(c, vec3(0.299, 0.587, 0.114));
                    c = mix(vec3(l), c, uGrade.x);
                    c *= vec3(1.0 + uGrade.w * 0.12, 1.0, 1.0 - uGrade.w * 0.12);
                    frag = vec4(clamp(c, 0.0, 1.0), 1.0);
                }
            }
            """;

    private static boolean inited, failed;
    private static int prog, vao;
    private static int uTex, uPrev, uMode, uMix, uStep, uGrade;

    // pełna rozdzielczość: kopia klatki + dwie tekstury na wynik (ping-pong dla motion blura)
    private static int fw, fh;
    private static int curTex;
    private static final int[] resTex = new int[2], resFbo = new int[2];
    private static int rd;
    private static boolean havePrev;
    private static long lastNs = System.nanoTime();

    // 1/4 rozdzielczości: rozmycie
    private static int bw, bh;
    private static final int[] blurTex = new int[2], blurFbo = new int[2];

    // zapisany stan OpenGL
    private static int sDraw, sRead, sProg, sVao, sActive, sTex0, sTex1;
    private static final int[] sVp = new int[4];
    private static boolean sBlend, sDepth, sCull, sScissor;

    private static void save() {
        sDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        sRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        sProg = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        sVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        sActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        sTex0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        sTex1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(sActive);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, sVp);
        sBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        sDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        sCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        sScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    }

    private static void restore() {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sDraw);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sRead);
        GL20.glUseProgram(sProg);
        GL30.glBindVertexArray(sVao);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sTex1);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sTex0);
        GL13.glActiveTexture(sActive);
        GL11.glViewport(sVp[0], sVp[1], sVp[2], sVp[3]);
        setCap(GL11.GL_BLEND, sBlend);
        setCap(GL11.GL_DEPTH_TEST, sDepth);
        setCap(GL11.GL_CULL_FACE, sCull);
        setCap(GL11.GL_SCISSOR_TEST, sScissor);
    }

    private static void setCap(int cap, boolean on) {
        if (on) GL11.glEnable(cap); else GL11.glDisable(cap);
    }

    private static int compile(int type, String src) {
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, src);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == 0)
            throw new IllegalStateException("shader: " + GL20.glGetShaderInfoLog(s, 2048));
        return s;
    }

    private static void init() {
        if (inited) return;
        inited = true;
        try {
            int vs = compile(GL20.GL_VERTEX_SHADER, VS), fs = compile(GL20.GL_FRAGMENT_SHADER, FS);
            prog = GL20.glCreateProgram();
            GL20.glAttachShader(prog, vs);
            GL20.glAttachShader(prog, fs);
            GL20.glLinkProgram(prog);
            if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == 0)
                throw new IllegalStateException("link: " + GL20.glGetProgramInfoLog(prog, 2048));
            GL20.glDeleteShader(vs);
            GL20.glDeleteShader(fs);
            uTex = GL20.glGetUniformLocation(prog, "uTex");
            uPrev = GL20.glGetUniformLocation(prog, "uPrev");
            uMode = GL20.glGetUniformLocation(prog, "uMode");
            uMix = GL20.glGetUniformLocation(prog, "uMix");
            uStep = GL20.glGetUniformLocation(prog, "uStep");
            uGrade = GL20.glGetUniformLocation(prog, "uGrade");
            vao = GL30.glGenVertexArrays();
            GL20.glUseProgram(prog);
            GL20.glUniform1i(uTex, 0);
            GL20.glUniform1i(uPrev, 1);
        } catch (Throwable t) {
            failed = true;
            System.err.println("[KosaVisuals] PostFx wyłączony: " + t);
        }
    }

    private static int newTexture(int w, int h) {
        int t = GL11.glGenTextures();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, t);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
        return t;
    }

    private static int newFbo(int tex) {
        int f = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, f);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("framebuffer incomplete");
        return f;
    }

    private static void freeFull() {
        if (curTex != 0) GL11.glDeleteTextures(curTex);
        for (int i = 0; i < 2; i++) {
            if (resTex[i] != 0) GL11.glDeleteTextures(resTex[i]);
            if (resFbo[i] != 0) GL30.glDeleteFramebuffers(resFbo[i]);
            resTex[i] = 0;
            resFbo[i] = 0;
        }
        curTex = 0;
    }

    private static void ensureFull(int w, int h) {
        if (curTex != 0 && w == fw && h == fh) return;
        freeFull();
        fw = w;
        fh = h;
        curTex = newTexture(w, h);
        for (int i = 0; i < 2; i++) {
            resTex[i] = newTexture(w, h);
            resFbo[i] = newFbo(resTex[i]);
        }
        havePrev = false;
        rd = 0;
    }

    private static void ensureBlur(int w, int h) {
        if (blurTex[0] != 0 && w == bw && h == bh) return;
        for (int i = 0; i < 2; i++) {
            if (blurTex[i] != 0) GL11.glDeleteTextures(blurTex[i]);
            if (blurFbo[i] != 0) GL30.glDeleteFramebuffers(blurFbo[i]);
        }
        bw = w;
        bh = h;
        for (int i = 0; i < 2; i++) {
            blurTex[i] = newTexture(w, h);
            blurFbo[i] = newFbo(blurTex[i]);
        }
    }

    /** Rysuje pełnoekranowy trójkąt wybranym trybem shadera do aktualnego celu. */
    private static void pass(int mode, int tex0, int tex1, int w, int h) {
        GL11.glViewport(0, 0, w, h);
        GL20.glUniform1i(uMode, mode);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex1);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex0);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    private static void prepare() {
        GL20.glUseProgram(prog);
        GL30.glBindVertexArray(vao);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    // ------------------------------------------------------------------ Motion Blur + Kolorki (koniec renderu świata)

    public static void onWorldEnd(WorldRenderContext ctx) {
        HoloConfig cfg = HoloConfig.get();
        boolean mb = cfg.motionBlur && cfg.motionBlurAmount > 0.01f;
        boolean cg = cfg.colorGrade;
        long now = System.nanoTime();
        float dt = MathHelper.clamp((now - lastNs) / 1_000_000_000f, 0.001f, 0.1f);
        lastNs = now;
        if (!mb && !cg) { havePrev = false; return; }
        if (failed) return;
        Window win = MinecraftClient.getInstance().getWindow();
        if (win == null) return;
        int w = win.getFramebufferWidth(), h = win.getFramebufferHeight();
        if (w < 2 || h < 2) return;

        save();
        try {
            init();
            if (failed) return;
            ensureFull(w, h);
            prepare();
            int target = sDraw;

            // 1) kopia aktualnej klatki
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, curTex);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
            int src = curTex;

            // 2) motion blur: wynik = mix(klatka, poprzedni wynik, k), niezależnie od FPS
            if (mb) {
                float keep = MathHelper.clamp(cfg.motionBlurAmount, 0f, 2f) * 0.465f;   // 200% = 0,93 na klatkę (60 FPS)
                float k = havePrev ? (float) Math.pow(keep, dt * 60.0) : 0f;
                int wr = 1 - rd;
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, resFbo[wr]);
                GL20.glUniform1f(uMix, k);
                pass(0, curTex, resTex[rd], w, h);
                rd = wr;
                havePrev = true;
                src = resTex[rd];
            } else {
                havePrev = false;
            }

            // 3) z powrotem do gry: z korekcją kolorów albo zwykła kopia
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target);
            if (cg) GL20.glUniform4f(uGrade, cfg.cgSat, cfg.cgContrast, cfg.cgBright, cfg.cgWarm);
            pass(cg ? 3 : 2, src, src, w, h);
        } catch (Throwable t) {
            failed = true;
            System.err.println("[KosaVisuals] PostFx (świat) wyłączony: " + t);
        } finally {
            restore();
        }
    }

    // ------------------------------------------------------------------ Rozmycie klienta (tło menu)

    /**
     * Rozmywa to, co jest już narysowane w grze (świat + HUD) - wołać na początku render() własnego menu,
     * przed rysowaniem jego elementów. amount: 0..1.
     */
    public static void blurBackground(DrawContext ctx, float amount) {
        if (failed || amount < 0.01f) return;
        Window win = MinecraftClient.getInstance().getWindow();
        if (win == null) return;
        int w = win.getFramebufferWidth(), h = win.getFramebufferHeight();
        if (w < 8 || h < 8) return;
        ctx.draw();   // dokończ to, co czeka w buforze rysowania GUI

        save();
        try {
            init();
            if (failed) return;
            int aw = Math.max(2, w / 4), ah = Math.max(2, h / 4);
            ensureBlur(aw, ah);
            prepare();
            int target = sDraw;

            // zmniejszenie klatki (filtrowane) do 1/4
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, blurFbo[0]);
            GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, aw, ah, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);

            float t = MathHelper.clamp(amount, 0f, 1f);
            int iters = 1 + Math.round(3f * t);
            float step = 0.6f + 1.7f * t;
            for (int i = 0; i < iters; i++) {
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, blurFbo[1]);
                GL20.glUniform2f(uStep, step / aw, 0f);
                pass(1, blurTex[0], blurTex[0], aw, ah);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, blurFbo[0]);
                GL20.glUniform2f(uStep, 0f, step / ah);
                pass(1, blurTex[1], blurTex[1], aw, ah);
            }

            // powiększenie z powrotem na ekran
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target);
            pass(2, blurTex[0], blurTex[0], w, h);
        } catch (Throwable t) {
            failed = true;
            System.err.println("[KosaVisuals] PostFx (rozmycie) wyłączony: " + t);
        } finally {
            restore();
        }
    }
}
