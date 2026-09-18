package com.localqbank.library
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.widget.*
import java.io.File
class BenPdfLibraryActivity:Activity(){
    override fun onCreate(b:Bundle?){super.onCreate(b);val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,12,16,16)}
        val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        bar.addView(TextView(this).apply{text="‹";textSize=30f;setOnClickListener{finish()}},LinearLayout.LayoutParams(42,48))
        bar.addView(TextView(this).apply{text="Imported PDFs";textSize=20f;setTypeface(null,android.graphics.Typeface.BOLD)},LinearLayout.LayoutParams(0,48,1f));root.addView(bar)
        val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};root.addView(ScrollView(this).apply{addView(list)},LinearLayout.LayoutParams(-1,0,1f));setContentView(root);refresh(list)}
    private fun refresh(list:LinearLayout){list.removeAllViews();val dir=File(filesDir,"ben_pdf_corpus");val fs=dir.listFiles{f->f.extension.equals("pdf",true)}?.sortedByDescending{it.lastModified()}.orEmpty()
        if(fs.isEmpty()){list.addView(TextView(this).apply{text="No PDFs imported yet.";textSize=15f;setPadding(8,24,8,24)});return}
        fs.forEach{pdf->val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
            row.addView(TextView(this).apply{text=pdf.name.substringAfter("_").removeSuffix(".pdf").replace("_"," ");textSize=14f},LinearLayout.LayoutParams(0,58,1f))
            row.addView(TextView(this).apply{text="OPEN";gravity=Gravity.CENTER;setOnClickListener{open(pdf)}},LinearLayout.LayoutParams(68,44))
            row.addView(TextView(this).apply{text="DELETE";gravity=Gravity.CENTER;setOnClickListener{pdf.delete();File(pdf.parentFile,pdf.nameWithoutExtension+".txt").delete();refresh(list)}},LinearLayout.LayoutParams(72,44))
            list.addView(row)}
    }
    private fun open(file:File){val d=android.app.Dialog(this);val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(8,8,8,8)}
        val image=ImageView(this).apply{adjustViewBounds=true};var page=0
        fun render(){runCatching{ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use{pfd->PdfRenderer(pfd).use{r->r.openPage(page).use{pg->val bm=Bitmap.createBitmap(pg.width,pg.height,Bitmap.Config.ARGB_8888);pg.render(bm,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);image.setImageBitmap(bm)}}}}}
        val controls=LinearLayout(this).apply{gravity=Gravity.CENTER}
        controls.addView(TextView(this).apply{text="‹";textSize=26f;setPadding(24,4,24,4);setOnClickListener{if(page>0){page--;render()}}})
        controls.addView(TextView(this).apply{text="›";textSize=26f;setPadding(24,4,24,4);setOnClickListener{val n=runCatching{ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use{PdfRenderer(it).pageCount}}.getOrDefault(page+1);if(page+1<n){page++;render()}}})
        box.addView(image,LinearLayout.LayoutParams(-1,0,1f));box.addView(controls);d.setContentView(box);d.show();d.window?.setLayout(-1,-1);render()}
}
