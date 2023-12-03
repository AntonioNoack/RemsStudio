package lite

import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.effects.MaskLayer
import org.joml.*

// todo Rem's Studio Lite: don't output images, just execute chained ffmpeg commands
// try to support as many features as possible with just ffmpeg
// goal: hyper speed

// todo concat: https://trac.ffmpeg.org/wiki/Concatenate
// file with following contents:
//  file '/path/to/file1.wav'
//  file '/path/to/file2.wav'
//  file '/path/to/file3.wav'
// ffmpeg -f concat -safe 0 -i mylist.txt -c copy output.wav
// ffmpeg -i "concat:input1.ts|input2.ts|input3.ts" -c copy output.ts
// if they have different codecs:
// ffmpeg -i input1.mp4 -i input2.webm -i input3.mov \
//  -filter_complex "[0:v:0][0:a:0][1:v:0][1:a:0][2:v:0][2:a:0]concat=n=3:v=1:a=1[outv][outa]" \
//  -map "[outv]" -map "[outa]" output.mkv
// todo black screen: https://stackoverflow.com/questions/11453082/how-to-generate-a-2hour-long-blank-video
// ffmpeg -t 7200 -s 640x480 -f rawvideo -pix_fmt rgb24 -r 25 -i /dev/zero empty.mpeg
// todo text: https://stackoverflow.com/questions/17623676/text-on-video-ffmpeg
// ffmpeg -i input.mp4 -vf "drawtext=fontfile=/path/to/font.ttf:text='Stack Overflow':fontcolor=white:fontsize=24:box=1:boxcolor=black@0.5:boxborderw=5:x=(w-text_w)/2:y=(h-text_h)/2" -codec:a copy output.mp4
// ffmpeg -i input.mp4 -vf "drawtext=fontfile=/path/to/font.ttf:text='Stack Overflow':fontcolor=white:fontsize=24:box=1:boxcolor=black@0.5:boxborderw=5:x=(w-text_w)/2:y=(h-text_h)/2,drawtext=fontfile=/path/to/font.ttf:text='Bottom right text':fontcolor=black:fontsize=14:x=w-tw-10:y=h-th-10" -codec:a copy output.mp4
// todo font without files: https://stackoverflow.com/questions/65002949/how-to-include-font-in-ffmpeg-command-without-using-the-fontfile-option/65027999#65027999
// ffmpeg -i input.webm -vf "drawtext=text='© Krishna':font='Times New Roman':x=(main_w-text_w-10):y=(main_h-text_h-10):fontsize=32:fontcolor=black:box=1:boxcolor=white@0.5:boxborderw=5" output.mp4
// todo fading in/out: https://dev.to/dak425/add-fade-in-and-fade-out-effects-with-ffmpeg-2bj7
// fade = video fade, afade = audio fade, st = start time, d = duration
// ffmpeg -i video.mp4 -vf "fade=t=in:st=0:d=3" -c:a copy out.mp4
// ffmpeg -i video.mp4 -vf "fade=t=out:st=10:d=5" -c:a copy out.mp4
// ffmpeg -i music.mp3 -af "afade=t=in:st=0:d=5" out.mp3
// ffmpeg -i music.mp3 -af "afade=t=out:st=5:d=5" out.mp3
// ffmpeg -i video.mp4 -vf "fade=t=in:st=0:d=10,fade=t=out:st=10:d=5" -c:a copy out.mp4
// todo overlay: https://stackoverflow.com/questions/35269387/ffmpeg-overlay-one-video-onto-another-video
// ffmpeg -i input.mov
//  -i overlay.mov \
//  -filter_complex "[1:v]setpts=PTS-10/TB[a]; \
//                 [0:v][a]overlay=enable=gte(t\,5):shortest=1[out]" \
//  -map [out] -map 0:a \
//  -c:v libx264 -crf 18 -pix_fmt yuv420p \
//  -c:a copy \
//  output.mov
// todo mixing: https://stackoverflow.com/questions/66256414/mixing-various-audio-and-video-sources-into-a-single-video
// ffmpeg -i test.png
//       -t 2 -i a.mp3
//       -t 5 -i without_sound.mp4
//       -t 1 -i b.mp3
//       -t 10 -i with_sound.mp4
//       -filter_complex "
//            [0]setpts=PTS-STARTPTS[s0];
//            [1]adelay=2000^|2000[s1];
//            [2]setpts=PTS-STARTPTS+7/TB[s2];
//            [3]adelay=5000^|5000[s3];
//            [4]setpts=PTS-STARTPTS+3/TB[s4];
//            [4:a]adelay=3000^|3000[t4];
//            [s1][s3][t4]amix=inputs=3[outa];
//            [s0][s4]overlay=100:100[o2];
//            [o2][s2]overlay=200:200[outv]
//       " -map [outa] -map [outv]
//       out.mp4 -y
// todo green-screen: https://stackoverflow.com/questions/8299252/ffmpeg-chroma-key-greenscreen-filter-for-images-video
// ffmpeg -i <base-video> -i <overlay-video> -filter_complex '[1:v]colorkey=0x<color>:<similarity>:<blend>[ckout];[0:v][ckout]overlay[out]' -map '[out]' <output-file>
// todo blurring: https://www.bannerbear.com/blog/how-to-apply-a-gaussian-blur-to-a-video-with-ffmpeg/
// ffmpeg -i man.mp4 -vf "boxblur=10" -c:a copy man-blur.mp4
// apply 5 times
// ffmpeg -i man.mp4 -vf "boxblur=50:5" -c:a copy man-blur-more-more.mp4
// ffmpeg -i man.mp4 -vf "gblur=sigma=10" -c:a copy man-gblur.mp4
// todo masking: https://superuser.com/questions/901099/ffmpeg-apply-blur-over-face
// https://ffmpeg.org/ffmpeg-filters.html#alphamerge
// https://ffmpeg.org/ffmpeg-filters.html#avgblur
// https://ffmpeg.org/ffmpeg-filters.html#overlay
// ffmpeg -i video.mp4 -i mask.png -filter_complex "[0:v][1:v]alphamerge,avgblur=10[alf];[0:v][alf]overlay[v]" -map "[v]" -map 0:a -c:v libx264 -c:a copy -movflags +faststart maskedblur.mp4
// todo 3d lut: https://ffmpeg.org/ffmpeg-filters.html#toc-lut3d-1
// todo custom shaders like Anime improvements???: https://github.com/mpv-player/mpv/wiki/User-Scripts#user-shaders
// todo rotating videos: https://www.baeldung.com/linux/ffmpeg-rotate-video
// ffmpeg -i big_buck_bunny_720p_1mb.mp4 -vf "transpose=1, transpose=1" output_transpose_multiple.mp4
// ffmpeg -i big_buck_bunny_720p_1mb.mp4 -vf "rotate=45*(PI/180)" output_rotate_45.mp4
// zoom & pan? https://ffmpeg.org/ffmpeg-filters.html#toc-zoompan
// todo perspective transform: https://stackoverflow.com/questions/61028674/perspective-correction-example, https://ffmpeg.org/pipermail/ffmpeg-user/2014-October/023901.html
// ffmpeg -hide_banner -i input.mkv -lavfi "perspective=x0=225:y0=0:x1=715:y1=385:x2=-60:y2=469:x3=615:y3=634:interpolation=linear" output.mkv
// ffmpeg -re -f lavfi -i "testsrc,drawbox=x=1:y=1:w=iw-2:h=ih-2:t=1:c=white,drawgrid=w=iw/10:h=ih/10:t=1:c=white at 0.5" -filter_complex "[0:v]perspective=x0=-100:y0=-100:x2=-100:y2=H+100[ovl1]; [0:v]pad=w=iw*2[ovl0]; [ovl0][ovl1]overlay=x=W/2[vidout]" -map "[vidout]" -pix_fmt yuv420p -f sdl -
// todo another way for layout: stacking, https://stackoverflow.com/questions/11552565/vertically-or-horizontally-stack-mosaic-several-videos-using-ffmpeg

typealias FFMPEGCommand = List<String>

fun flatten(self0: Transform, globalTime: Double): List<Transform> {
    val list = ArrayList<Transform>()
    fun process(self: Transform, m: Matrix4f, c: Vector4f) {
        m.mul(self.getLocalTransform(globalTime, self.parent)) // correct order?
        c.mul(self.getLocalColor()) // ok? todo without fade-in/out
        val clone = self.clone()
        if (self !is MaskLayer) {
            for (child in self.children) {
                process(self, Matrix4f(m), Vector4f(c))
            }
            clone.children.clear()
        }
        clone.position.clear()
        clone.position.set(m.getTranslation(Vector3f()))
        clone.rotationYXZ.clear()
        val q = m.getUnnormalizedRotation(Quaterniond())
        // todo is eulerAngleDegrees correct???
        clone.rotationYXZ.set(Vector3f(q.toEulerAnglesDegrees(Vector3d())))
        clone.scale.clear()
        clone.scale.set(m.getScale(Vector3f()))
        list.add(clone)
    }
    process(self0, Matrix4f(), Vector4f(1f))
    return list
}

fun preRender(input: Transform): List<FFMPEGCommand> {
    val flat = flatten(input, 0.0)
    // todo transform local translations into global translations
    // todo first step: flatten hierarchy (where possible)
    input.position
    input.scale
    input.fadeIn
    input.fadeOut
    TODO()
}

fun main() {

    // todo check whether a project can be rendered as "lite"
    // todo if so, render them directly in ffmpeg

}