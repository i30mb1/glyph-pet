package n7.glyphpet.tools

import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyphpet.pet.Mood
import n7.glyphpet.pet.PetEngine
import n7.glyphpet.pet.Scene
import n7.glyphpet.pet.SpeciesForms
import n7.glyphpet.pet.Stage
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.sin
import kotlin.random.Random

/**
 * Every frame of a pet's meal and then its game (until it is ended and the pet is home), as an HTML page with a
 * player: play, step frame by frame, and the frame number to point at. Runs only with PLAY_OUT set (an .html path);
 * PLAY_KIND picks the pet, PLAY_STAGE its stage.
 */
class FramePlayer {
    @Test
    fun play() {
        val out = System.getenv("PLAY_OUT") ?: return assumeTrue(false)
        val kind = System.getenv("PLAY_KIND") ?: "cat"
        val stage = Stage.valueOf((System.getenv("PLAY_STAGE") ?: "adult").uppercase())
        val s = SpeciesForms.of(kind, stage)
        val e = PetEngine(Random(5), autoHabits = false, autoSignatures = false)
        val step = 40L
        val frames = ArrayList<Pair<String, IntArray>>()
        fun run(label: String, ms: Long, before: (Long) -> Unit = {}) {
            var t = 0L
            while (t < ms) {
                before(t)
                val tilt = (0.6 * sin((frames.size * step) / 900.0)).toFloat()
                frames += label to e.tick(step, Scene(s, Mood.CONTENT, tilt = tilt, tiltY = 0.3f)).copyOf()
                t += step
            }
        }
        run("покой", 600)
        e.feed(true)
        run("ест", 3600)
        e.trick(kind, 7L)
        run("игра", 12000)
        e.stopTrick()
        run("домой", 3200)
        val vis = (0 until 625).map { GlyphMatrixSpec.isVisible(it % 25, it / 25) }
        val data = frames.joinToString(",\n") { (l, f) -> "[\"$l\",\"" + f.joinToString("") { v -> "0123456789"[(v.coerceIn(0, 4095) * 9 + 4094) / 4095] .toString() } + "\"]" }
        File(out).writeText(PAGE.replace("/*DATA*/", data).replace("/*VIS*/", vis.joinToString(",") { if (it) "1" else "0" })
            .replace("/*STEP*/", step.toString()).replace("/*TITLE*/", "$kind · ${stage.name.lowercase()}"))
    }

    private companion object {
        val PAGE = """<!doctype html><html lang="ru"><head><meta charset="utf-8"><title>Кадры: /*TITLE*/</title>
<style>body{margin:0;background:#0a0a0a;color:#ddd;font:15px system-ui,sans-serif}main{max-width:1100px;margin:0 auto;padding:20px}
.top{display:flex;gap:20px;align-items:flex-start;flex-wrap:wrap}canvas{width:420px;height:420px;background:#050505;border-radius:50%}
.ctl{display:flex;flex-direction:column;gap:10px;min-width:300px}button{background:#262626;color:#eee;border:1px solid #333;border-radius:8px;padding:8px 12px;font:inherit;cursor:pointer}
.big{font:600 28px system-ui;color:#fff}.lab{color:#f66;font-size:18px}input[type=range]{width:100%}
.strip{display:grid;grid-template-columns:repeat(auto-fill,minmax(90px,1fr));gap:6px;margin-top:20px}.strip div{font-size:11px;color:#888;text-align:center;cursor:pointer}.strip canvas{width:90px;height:90px}
.strip .on canvas{outline:2px solid #f44}</style></head><body><main>
<h2>Кадры: /*TITLE*/ — еда, потом игра, потом домой</h2>
<div class="top"><canvas id="cv" width="500" height="500"></canvas><div class="ctl">
<div class="big" id="num"></div><div class="lab" id="lab"></div>
<input type="range" id="seek" min="0" value="0">
<div><button id="prev">◀ кадр</button> <button id="play">▶ играть</button> <button id="next">кадр ▶</button></div>
<div><button id="slow">скорость: 1×</button></div>
<p style="color:#999">Стрелки ← → — по кадру, пробел — пуск/пауза. Скажи номер кадра, где не так.</p></div></div>
<div class="strip" id="strip"></div></main><script>
const F=[/*DATA*/];const VIS=[/*VIS*/];const STEP=/*STEP*/;let i=0,playing=false,speed=1;
function draw(cv,f,big){const g=cv.getContext('2d'),s=cv.width/25;g.fillStyle='#050505';g.fillRect(0,0,cv.width,cv.height);
 for(let k=0;k<625;k++){if(!VIS[k])continue;const x=k%25,y=(k/25)|0,v=+f[k]/9,b=Math.sqrt(v);const c=v>0?Math.round(40+215*b):21;
  g.fillStyle=`rgb(${'$'}{c},${'$'}{c},${'$'}{c})`;g.beginPath();g.arc(x*s+s/2,y*s+s/2,s*.42,0,7);g.fill()}}
const cv=document.getElementById('cv'),seek=document.getElementById('seek');seek.max=F.length-1;
const strip=document.getElementById('strip');const thumbs=[];
F.forEach((f,k)=>{if(k%5)return;const d=document.createElement('div');d.innerHTML='<canvas width="100" height="100"></canvas><br>#'+k+' '+f[0];d.onclick=()=>show(k);strip.appendChild(d);draw(d.firstChild,f[1]);thumbs.push([k,d])});
function show(k){i=Math.max(0,Math.min(F.length-1,k));draw(cv,F[i][1]);seek.value=i;document.getElementById('num').textContent='кадр #'+i+' · '+(i*STEP/1000).toFixed(2)+' с';
 document.getElementById('lab').textContent=F[i][0];thumbs.forEach(([kk,d])=>d.className=(kk<=i&&i<kk+5)?'on':'')}
seek.oninput=()=>show(+seek.value);prev.onclick=()=>show(i-1);next.onclick=()=>show(i+1);
play.onclick=()=>{playing=!playing;play.textContent=playing?'❚❚ пауза':'▶ играть'};
slow.onclick=()=>{speed=speed==1?0.25:speed==0.25?0.1:1;slow.textContent='скорость: '+speed+'×'};
document.onkeydown=e=>{if(e.key=='ArrowLeft')show(i-1);if(e.key=='ArrowRight')show(i+1);if(e.key==' '){e.preventDefault();play.onclick()}};
let acc=0,last=performance.now();function loop(n){const dt=n-last;last=n;if(playing){acc+=dt*speed;while(acc>=STEP){acc-=STEP;show(i+1>=F.length?0:i+1)}}requestAnimationFrame(loop)}
show(0);requestAnimationFrame(loop);</script></body></html>"""
    }
}
