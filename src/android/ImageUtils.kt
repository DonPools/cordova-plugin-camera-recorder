
import android.graphics.ImageFormat
import android.graphics.Rect

import android.graphics.YuvImage
import android.media.Image
import java.io.ByteArrayOutputStream
import java.lang.RuntimeException
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
}