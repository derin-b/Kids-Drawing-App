package com.example.kidsdrawingapp

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import com.example.kidsdrawingapp.databinding.ActivityMainBinding
import com.example.kidsdrawingapp.databinding.CustomProgressBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception

class MainActivity : AppCompatActivity() {

    lateinit var drawingView: DrawingView
    lateinit var binding: ActivityMainBinding
    lateinit var mImageButtonCurrentPaint: ImageButton
    private var customProgressDialog: Dialog? = null

    private val openGalleryLauncher : ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            result->
            if(result.resultCode== RESULT_OK && result.data != null){
                val imageBackground = binding.ivBackground
                imageBackground.setImageURI(result.data?.data)
            }
        }

    private val requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
        { permissions->
            permissions.entries.forEach{
                val permissionsName = it.key
                val isGranted = it.value
                if(isGranted){
                    Toast.makeText(this@MainActivity,
                        "Permission granted; now you can read storage files",
                        Toast.LENGTH_LONG).show()
                    val pickIntent = Intent(Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI)//URI is the location on your device
                    openGalleryLauncher.launch(pickIntent)
                }else{
                    if(permissionsName == Manifest.permission.READ_EXTERNAL_STORAGE){
                        Toast.makeText(this,
                            "Oops you just denied permission", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        drawingView = binding.drawingView
        drawingView.setBrushSize(20.toFloat())

        val linearLayout = binding.llPaintColors
        mImageButtonCurrentPaint = linearLayout[1] as ImageButton
        mImageButtonCurrentPaint.setImageDrawable( ContextCompat.getDrawable(
            this, R.drawable.pallet_selected))

        val ibBrush = binding.ibBrush
        ibBrush.setOnClickListener {
            showBrushSizeDialog()
        }

        val undoBtn = binding.ibUndo
        undoBtn.setOnClickListener {
            drawingView.onCLickUndo()
        }

        val redoBtn = binding.ibRedo
        redoBtn.setOnClickListener {
            drawingView.onCLickRedo()
        }

        val imageButton = binding.ibGallery
        imageButton.setOnClickListener {
            requestStoragePermission()
        }
        val saveBtn = binding.ibSave
        saveBtn.setOnClickListener {
            //before you can save (write) you need to ensure you have permission to read
            if(isReadStorageAllowed()){
                showProgressDialog()
                lifecycleScope.launch {
                    val flDrawingView = binding.flDrawingView
                    saveBitmapFile(getBitmapFromView(flDrawingView))
                }
            }
        }
    }
    private fun showBrushSizeDialog(){
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush)
        brushDialog.setTitle("Brush Size : ")
        val smallBtn: ImageButton = brushDialog.findViewById(R.id.ib_small_brush)
        smallBtn.setOnClickListener {
            drawingView.setBrushSize(10.toFloat())
            brushDialog.dismiss()
        }
        val mediumBtn: ImageButton = brushDialog.findViewById(R.id.ib_medium_brush)
        mediumBtn.setOnClickListener {
            drawingView.setBrushSize(20.toFloat())
            brushDialog.dismiss()
        }
        val largeBtn: ImageButton = brushDialog.findViewById(R.id.ib_large_brush)
        largeBtn.setOnClickListener {
            drawingView.setBrushSize(30.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()
    }

    fun paintClicked(view: View){
        if(view !== mImageButtonCurrentPaint){
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            drawingView.setColor(colorTag)

            imageButton.setImageDrawable( ContextCompat.getDrawable(
                this, R.drawable.pallet_selected))

            mImageButtonCurrentPaint.setImageDrawable( ContextCompat.getDrawable(
                this, R.drawable.pallet_normal))

            mImageButtonCurrentPaint = view

        }
    }

    private fun showRationaleDialog(
        title: String,
        message: String){
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
            .setPositiveButton("Cancel"){dialog, _ ->
                dialog.dismiss()
            }
        builder.create().show()
    }

    private fun isReadStorageAllowed(): Boolean{
        val result = ContextCompat.checkSelfPermission(this,
        Manifest.permission.READ_EXTERNAL_STORAGE)

        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission(){
        if(ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
        ){
            showRationaleDialog("Kids Drawing App",
                "Kids Drawing App needs to Access Your External Storage")
        }else{
            requestPermission.launch(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }

    private fun getBitmapFromView(view: View): Bitmap{
        val returnedBitmap = Bitmap.createBitmap(
            view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if(bgDrawable !=null){
            bgDrawable.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return returnedBitmap
    }

    private suspend fun saveBitmapFile(mBitmap: Bitmap?): String{
        var result = ""
        withContext(Dispatchers.IO){
            if(mBitmap != null){
                try{
                    val bytes =ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)

                    //Creating a location for the file to save
                    val f = File(externalCacheDir?.absoluteFile.toString()
                    + File.separator + "KidsDrawing_" +
                            System.currentTimeMillis()/1000 + "png")

                    //creating a fileOutput Stream
                    val fo = FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()

                    result = f.absolutePath
                    
                    runOnUiThread {
                        cancelProgressDialog()
                        if(result.isNotEmpty()){
                            Toast.makeText(this@MainActivity,
                                "File saved successfully : $result",
                                Toast.LENGTH_SHORT).show()
                            shareImage(result)
                        }else{
                            Toast.makeText(this@MainActivity,
                                "Something went wrong while saving the file",
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                }catch (e:Exception){
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }
    private fun showProgressDialog(){
        customProgressDialog = Dialog(this)
        customProgressDialog?.setContentView(R.layout.custom_progress)

        customProgressDialog?.show()
    }
    private fun cancelProgressDialog(){
        if(customProgressDialog != null){
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }

    private fun shareImage(result:String){
        MediaScannerConnection.scanFile(this, arrayOf(result), null){
            path,uri->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.type = "ïmage/png"
            startActivity(Intent.createChooser(shareIntent,"Share"))
        }
    }
}