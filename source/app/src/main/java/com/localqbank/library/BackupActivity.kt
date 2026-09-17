package com.localqbank.library

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.documentfile.provider.DocumentFile
import java.text.SimpleDateFormat
import java.util.*

class BackupActivity: Activity() {
    private lateinit var manager: BackupManager
    private lateinit var status: TextView
    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
    override fun onCreate(b:Bundle?){super.onCreate(b);SystemUi.immersive(this);manager=BackupManager(this);setContentView(build());refreshStatus()
        TransitionCoordinator.install(this)}
    private fun build():LinearLayout{
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=ThemeManager.backgroundDrawable(this@BackupActivity)}
        val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8));setBackgroundColor(if(ThemeManager.isDark(this@BackupActivity))Color.rgb(18,31,45) else Color.rgb(22,72,112))}
        val back=TextView(this).apply{text="←";textSize=23f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;background=GradientDrawable().apply{setColor(if(ThemeManager.isDark(this@BackupActivity))Color.rgb(31,43,58) else Color.rgb(28,70,103));cornerRadius=12f*resources.displayMetrics.density};setOnClickListener{finish()}}
        bar.addView(back,LinearLayout.LayoutParams(dp(56),dp(56)).apply{setMargins(0,0,dp(8),0)})
        val title=TextView(this).apply{text="Backup & Restore";textSize=20f;setTypeface(null,Typeface.BOLD);setTextColor(Color.WHITE)};bar.addView(title,LinearLayout.LayoutParams(0,-2,1f));root.addView(bar)
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(18),dp(18),dp(24))}
        val intro=TextView(this).apply{text="Your study progress is saved immediately on the phone. Backup is your safety net if Rovex is deleted, the phone is lost, or you move to a new device.";textSize=15f;setTextColor(ThemeManager.text(this@BackupActivity));setLineSpacing(0f,1.15f)};body.addView(intro)
        val howTitle=TextView(this).apply{text="HOW BACKUP WORKS — THE SIMPLE VERSION";textSize=14f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.accent(this@BackupActivity));setPadding(0,dp(20),0,dp(8))};body.addView(howTitle)
        val how=TextView(this).apply{
            text = """① While you study, Rovex saves your progress on the device automatically.

② Automatic Backup = a small progress safety copy. It protects answers, correct/wrong status, bookmarks, review flags, notes and SRS settings — but NOT the QBank database or full flashcard collection.

③ Full Backup = your complete Rovex snapshot. It includes the QBank database, flashcards, flashcard media/knowledge, settings and other durable app data. Create/export this manually.

④ For real protection after uninstalling Rovex, keep a backup OUTSIDE the app — preferably a connected Google Drive folder or another safe location.

⑤ After reinstall: import your QBank first if you only have a Progress Backup. Then restore the Progress Backup. If you have a Full Backup, use Restore Full Backup instead; it can restore the databases and app data from that snapshot.

⭐ BEST ROUTINE: Let automatic backups run → connect a backup folder → occasionally EXPORT/UPLOAD a Full Backup."""
            textSize=14f;setTextColor(ThemeManager.text(this@BackupActivity));setLineSpacing(0f,1.12f);background=GradientDrawable().apply{setColor(ThemeManager.elevated(this@BackupActivity));cornerRadius=16f*resources.displayMetrics.density;setStroke(dp(1),if(ThemeManager.isDark(this@BackupActivity))Color.rgb(55,69,86) else Color.rgb(210,223,231))};setPadding(dp(14),dp(14),dp(14),dp(14))
        };body.addView(how,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,dp(8))})
        val scenarioTitle=TextView(this).apply{text="YOUR 1-MONTH → REINSTALL SCENARIO";textSize=14f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.accent(this@BackupActivity));setPadding(0,dp(18),0,dp(8))};body.addView(scenarioTitle)
        val scenario=TextView(this).apply{
            text = """If Rovex is deleted after one month:

1. Install Rovex again with the same app/package.
2. If you made only Progress Backups: import the QBank HTML/APKG again.
3. Open Backup & Restore → connect the SAME backup folder (or choose your exported .qbackup file).
4. Tap RESTORE PROGRESS BACKUP. Your saved study progress, bookmarks, notes and SRS settings come back.
5. If you made a Full Backup, use RESTORE FULL BACKUP instead. Rovex verifies it, stages it safely, then you close/reopen Rovex to finish the restore.

⚠️ Important: automatic local snapshots live inside Rovex's private storage. Uninstalling the app can remove them. Do not treat them as your only backup."""
            textSize=14f;setTextColor(ThemeManager.text(this@BackupActivity));setLineSpacing(0f,1.12f);background=GradientDrawable().apply{setColor(ThemeManager.elevated(this@BackupActivity));cornerRadius=16f*resources.displayMetrics.density;setStroke(dp(1),if(ThemeManager.isDark(this@BackupActivity))Color.rgb(55,69,86) else Color.rgb(210,223,231))};setPadding(dp(14),dp(14),dp(14),dp(14))
        };body.addView(scenario,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,dp(8))})
        val local=TextView(this).apply{text="LOCAL SAFETY\nAutomatic snapshots: last 8 kept on this device";textSize=14f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.accent(this@BackupActivity));setPadding(0,dp(24),0,dp(10))};body.addView(local)
        val now=button("BACK UP NOW"){Thread{val ok=manager.backupNow();runOnUiThread{toast(if(ok)"Backup completed" else "Backup could not be created");refreshStatus()}}.start()};body.addView(now,lp())
        val full=button("CREATE FULL BACKUP") { Thread { val f=manager.createFullBackup(); runOnUiThread { toast(if(f!=null) "Full backup created" else "Full backup could not be created"); refreshStatus() } }.start() };body.addView(full,lp())
        val export=button("EXPORT PROGRESS BACKUP FILE"){val i=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{type="application/octet-stream";putExtra(Intent.EXTRA_TITLE,"Q_Progress_${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",Locale.US).format(Date())}.qbackup");addCategory(Intent.CATEGORY_OPENABLE)};startActivityForResult(i,11)};body.addView(export,lp())
        val exportFull=button("EXPORT FULL BACKUP FILE"){startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{type="application/octet-stream";putExtra(Intent.EXTRA_TITLE,"Rovex_Full_${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",Locale.US).format(Date())}.qbackup");addCategory(Intent.CATEGORY_OPENABLE)},15)};body.addView(exportFull,lp())
        val cloud=TextView(this).apply{text="GOOGLE DRIVE / CLOUD FOLDER\nChoose a folder using Android's file picker. Automatic cloud backup is a small progress snapshot; Full Backup is available as a separate manual upload. The QBank HTML source is never uploaded by this feature.";textSize=14f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.accent(this@BackupActivity));setPadding(0,dp(24),0,dp(10))};body.addView(cloud)
        val choose=button(if(manager.treeUri()!=null)"CHANGE BACKUP FOLDER" else "CONNECT BACKUP FOLDER"){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),12)};body.addView(choose,lp())
        val cloudNow=button("BACK UP TO CONNECTED FOLDER"){Thread{val ok=manager.backupNow();runOnUiThread{toast(if(ok)"Cloud backup completed" else "Cloud backup failed — check folder access");refreshStatus()}}.start()};body.addView(cloudNow,lp())
        val fullCloud=button("UPLOAD FULL BACKUP TO CONNECTED FOLDER"){Thread{val f=manager.createFullBackup();val ok=f!=null && manager.uploadFullBackup(f);runOnUiThread{toast(if(ok)"Full backup uploaded" else "Full cloud backup failed — check folder access");refreshStatus()}}.start()};body.addView(fullCloud,lp())
        val fullRestore=button("RESTORE FULL BACKUP") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="application/octet-stream";putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("application/octet-stream","application/zip","*/*"));addCategory(Intent.CATEGORY_OPENABLE)},14) };body.addView(fullRestore,lp())
        val restore=button("RESTORE PROGRESS BACKUP"){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="application/octet-stream";putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("application/octet-stream","application/json","*/*"));addCategory(Intent.CATEGORY_OPENABLE)},13)};body.addView(restore,lp())
        status=TextView(this).apply{textSize=13f;setTextColor(ThemeManager.muted(this@BackupActivity));setPadding(0,dp(20),0,0)};body.addView(status)
        root.addView(ScrollView(this).apply{addView(body)},LinearLayout.LayoutParams(-1,0,1f));return root
    }
    private fun lp()=LinearLayout.LayoutParams(-1,dp(50)).apply{setMargins(0,dp(5),0,dp(5))}
    private fun button(text:String,click:()->Unit)=TextView(this).apply{this.text=text;textSize=14f;setTypeface(null,Typeface.BOLD);gravity=Gravity.CENTER;setTextColor(ThemeManager.text(this@BackupActivity));background=GradientDrawable().apply{setColor(ThemeManager.elevated(this@BackupActivity));cornerRadius=14f*resources.displayMetrics.density;setStroke(dp(1),if(ThemeManager.isDark(this@BackupActivity))Color.rgb(55,69,86) else Color.rgb(210,223,231))};setOnClickListener{click()}}
    private fun refreshStatus(){val uri=manager.treeUri();val date=manager.lastBackup();val d=if(date==0L)"Never" else SimpleDateFormat("dd MMM yyyy, hh:mm a",Locale.getDefault()).format(Date(date));status.text="Automatic Rovex backup folder: ${manager.automaticBackupLocation()}\nConnected folder: ${if(uri==null)"Not connected" else (DocumentFile.fromTreeUri(this,uri)?.name ?: "Selected folder")}\nLast local/cloud backup: $d"}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    override fun onActivityResult(req:Int,res:Int,data:Intent?){
        super.onActivityResult(req,res,data)
        if(res!=RESULT_OK||data?.data==null)return
        val uri=data?.data ?: return
        when(req){
            11->Thread{
                val f=manager.exportFile()
                if(f==null){runOnUiThread{if(!isFinishing&&!isDestroyed)toast("Could not create backup")};return@Thread}
                val ok=runCatching{contentResolver.openOutputStream(uri)?.use{o->f.inputStream().use{it.copyTo(o,1024*1024)}}!=null}.getOrDefault(false)
                runOnUiThread{if(!isFinishing&&!isDestroyed)toast(if(ok)"Backup exported" else "Backup export failed")}
            }.start()
            12->{manager.setTreeUri(uri);Thread{
                val ok=manager.backupNow()
                runOnUiThread{if(!isFinishing&&!isDestroyed){toast(if(ok)"Backup folder connected" else "Folder connected, but backup failed");refreshStatus()}}
            }.start()}
            13->Thread{
                val ok=manager.restoreFromUri(uri)
                runOnUiThread{if(!isFinishing&&!isDestroyed){toast(if(ok){"Progress restored. Re-open the QBank to verify."}else"Restore failed: invalid or damaged backup");refreshStatus()}}
            }.start()
            14->Thread{
                val ok=manager.stageFullRestore(uri)
                runOnUiThread{if(!isFinishing&&!isDestroyed){toast(if(ok){"Full backup verified and staged. Close and reopen Rovex to complete restore."}else"Full restore failed: invalid, damaged, or incompatible backup");refreshStatus()}}
            }.start()
            15->Thread{
                val f=manager.createFullBackup()
                if(f==null){runOnUiThread{if(!isFinishing&&!isDestroyed)toast("Could not create full backup")};return@Thread}
                val ok=runCatching{contentResolver.openOutputStream(uri)?.use{o->f.inputStream().use{it.copyTo(o,1024*1024)}}!=null}.getOrDefault(false)
                runOnUiThread{if(!isFinishing&&!isDestroyed)toast(if(ok)"Full backup exported" else "Full backup export failed")}
            }.start()
        }
    }
}
