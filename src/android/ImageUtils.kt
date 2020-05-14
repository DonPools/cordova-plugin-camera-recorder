import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.renderscript.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer



object ImageUtil {
    fun jpegImageToByteArray(image: Image): ByteArray? {
        var data: ByteArray? = null
        if (image.getFormat() === ImageFormat.JPEG) {
            val planes: Array<Image.Plane> = image.getPlanes()
            val buffer: ByteBuffer = planes[0].getBuffer()
            data = ByteArray(buffer.capacity())
            buffer.get(data)
            return data
        }

        throw(RuntimeException("Image is not jpeg format."))
    }

    fun YUV_420_888toNV21(image: Image): ByteArray {
        val nv21: ByteArray
        val yBuffer: ByteBuffer = image.getPlanes().get(0).getBuffer()
        val uBuffer: ByteBuffer = image.getPlanes().get(1).getBuffer()
        val vBuffer: ByteBuffer = image.getPlanes().get(2).getBuffer()
        val ySize: Int = yBuffer.remaining()
        val uSize: Int = uBuffer.remaining()
        val vSize: Int = vBuffer.remaining()
        nv21 = ByteArray(ySize + uSize + vSize)

        //U and V are swapped
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        return nv21
    }

    fun NV21toJPEG(nv21: ByteArray, width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        yuv.compressToJpeg(Rect(0, 0, width, height), 100, out)
        return out.toByteArray()
    }

    fun NV21toBitmap(context: Context?, nv21: ByteArray, width: Int, height: Int): Bitmap {
        return NV21ToBitmapHelper(context).nv21ToBitmap(nv21, width, height)
    }

}

class NV21ToBitmapHelper(context: Context?) {
    private val rs: RenderScript
    private val yuvToRgbIntrinsic: ScriptIntrinsicYuvToRGB
    private var `in`: Allocation? = null
    private var out: Allocation? = null

    init {
        rs = RenderScript.create(context)
        yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))
    }

    fun nv21ToBitmap(nv21: ByteArray, width: Int, height: Int): Bitmap {
        val yuvType = Type.Builder(rs, Element.U8(rs)).setX(nv21.size)
        `in` = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT)
        val rgbaType = Type.Builder(rs, Element.RGBA_8888(rs)).setX(width).setY(height)
        out = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT)
        `in`!!.copyFrom(nv21)

        yuvToRgbIntrinsic.setInput(`in`)
        yuvToRgbIntrinsic.forEach(out)

        val bmpout = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out!!.copyTo(bmpout)
        return bmpout
    }
}