package com.localqbank.library

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import android.content.ActivityNotFoundException
import android.widget.*
import kotlin.math.abs

/** High-speed, exam-oriented flashcard reviewer with safe offline SRS controls. */
class FlashcardStudyActivity : Activity() {
    private lateinit var db: FlashcardDb
    private lateinit var cardView: WebView
    private lateinit var counter: TextView
    private lateinit var progress: ProgressBar
    private lateinit var bookmark: TextView
    private lateinit var mark: TextView
    private lateinit var reveal: TextView
    private lateinit var ratingRow: LinearLayout
    private lateinit var modeLabel: TextView
    private lateinit var cardFrame: FrameLayout

    private var deckId = 0L
    private var mode = "due"
    private var position = 0
    private var showingBack = false
    private var reverse = false
    private var shuffle = false
    private var sessionLimit = 0
    private var count = 0
    private var studied = 0
    private var downX = 0f
    private var downY = 0f
    private var swipeLockedUntil = 0L
    private var startedAt = 0L
    private var elapsedSeconds = 0L
    private var lastRatedCardId = 0L
    private var cardViewDestroyed = false
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerTick = object : Runnable { override fun run() {
        if (!isFinishing && !isDestroyed) {
            elapsedSeconds=(System.currentTimeMillis()-startedAt)/1000L
            updateModeLabel()
            timerHandler.postDelayed(this,1000L)
        }
    } }
    private val imageBridge = object {
        @JavascriptInterface fun reveal() { runOnUiThread { if(!showingBack){ showingBack=true; show() } } }
        @JavascriptInterface fun open(src:String?) {
            if(src.isNullOrBlank()) return
            val ok = src.startsWith("https://",true) || src.startsWith("http://",true) || src.startsWith("data:image/",true) ||
                (src.startsWith("file://",true) && runCatching {
                    java.io.File(android.net.Uri.parse(src).path.orEmpty()).canonicalPath.startsWith(java.io.File(filesDir,"flashcard_media").canonicalPath)
                }.getOrDefault(false))
            if(ok) runOnUiThread { showImageFullscreen(src) }
        }
    }

    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
    private fun rounded(fill:Int,r:Int)=android.graphics.drawable.GradientDrawable().apply{setColor(fill);cornerRadius=dp(r).toFloat()}
    private fun button(text:String, fill:Int, click:(TextView)->Unit)=TextView(this).apply{this.text=text;textSize=12.5f;gravity=Gravity.CENTER;setTypeface(null,Typeface.BOLD);setTextColor(Color.WHITE);background=android.graphics.drawable.GradientDrawable().apply{setColor(fill);cornerRadius=dp(14).toFloat()};setOnClickListener{click(this)}}

    override fun onCreate(b:Bundle?) {
        super.onCreate(b)
        try {
            SystemUi.immersive(this); db=FlashcardDb(this)
            // Foreground flashcard study is always interactive; the central battery engine
            // only supplies policy to background/bulk work and does not throttle card taps.
            AppManagers.battery.policy(RovexBatteryManager.WorkClass.INTERACTIVE)
            deckId=intent.getLongExtra("deckId",0L)
            mode=intent.getStringExtra("mode") ?: when { intent.getBooleanExtra("bookmarksOnly",false)->"bookmarked"; deckId<0L->"due"; else->"deck" }
            sessionLimit=intent.getIntExtra("limit",0).coerceAtLeast(0)
            reverse=intent.getBooleanExtra("reverse",false); shuffle=intent.getBooleanExtra("shuffle",false)
            val prefs = getPreferences(0)
            position=prefs.getInt(key("pos"),0).coerceAtLeast(0)
            showingBack=prefs.getBoolean(key("back"),false)
            studied=prefs.getInt(key("studied"),0).coerceAtLeast(0)
            elapsedSeconds=prefs.getLong(key("elapsed"),0L).coerceAtLeast(0L)
            position=b?.getInt("fc.position",position)?.coerceAtLeast(0) ?: position
            showingBack=b?.getBoolean("fc.showingBack",showingBack) ?: showingBack
            studied=b?.getInt("fc.studied",studied)?.coerceAtLeast(0) ?: studied
            elapsedSeconds=b?.getLong("fc.elapsedSeconds",elapsedSeconds)?.coerceAtLeast(0L) ?: elapsedSeconds
            startedAt=System.currentTimeMillis()-elapsedSeconds*1000L
            setContentView(build()); show(); timerHandler.post(timerTick)
        } catch(t:Exception) { Toast.makeText(this,"Unable to open flashcards: ${t.message ?: t.javaClass.simpleName}",Toast.LENGTH_LONG).show(); finish() }
    }
    private fun key(s:String)="${s}_${deckId}_${mode}"
    private fun currentCount():Int { val raw=if(mode=="deck")db.continuousCardCount("all",deckId)else db.continuousCardCount(mode);return if(sessionLimit>0)minOf(raw,sessionLimit)else raw }
    private fun currentCard():FlashcardDb.Card?=if(mode=="deck")db.modeCardAt("all",position,deckId)else db.modeCardAt(mode,position)

    private fun build():LinearLayout {
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=ThemeManager.backgroundDrawable(this@FlashcardStudyActivity)}

        // Thin, readable header. The counter stays on the first line; the study status is
        // deliberately a separate plain line so it never gets squeezed into tiny text.
        val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(2),dp(8),dp(2));background=ThemeManager.backgroundDrawable(this@FlashcardStudyActivity)}
        bar.addView(TextView(this).apply{text="←";textSize=22f;gravity=Gravity.CENTER;setTextColor(ThemeManager.text(this@FlashcardStudyActivity));background=rounded(ThemeManager.elevated(this@FlashcardStudyActivity),10);setOnClickListener{finish()}},LinearLayout.LayoutParams(dp(40),dp(34)))
        counter=TextView(this).apply{textSize=15f;setTypeface(null,Typeface.BOLD);setTextColor(ThemeManager.text(this@FlashcardStudyActivity));gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),0,dp(6),0);maxLines=1}
        bar.addView(counter,LinearLayout.LayoutParams(0,dp(34),1f))
        val menu=TextView(this).apply{text="⋮";textSize=23f;gravity=Gravity.CENTER;setTextColor(ThemeManager.text(this@FlashcardStudyActivity));setOnClickListener{showReviewerMenu()}}
        bar.addView(menu,LinearLayout.LayoutParams(dp(36),dp(34)))
        root.addView(bar)
        modeLabel=TextView(this).apply{textSize=10.5f;setTextColor(ThemeManager.muted(this@FlashcardStudyActivity));gravity=Gravity.CENTER_VERTICAL;setPadding(dp(16),0,dp(16),0);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
        root.addView(modeLabel,LinearLayout.LayoutParams(-1,dp(20)))
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{max=100};root.addView(progress,LinearLayout.LayoutParams(-1,dp(3)))

        // Card surface + top-right stacked bookmark/mark controls.
        cardFrame=FrameLayout(this).apply{setPadding(dp(10),dp(7),dp(10),dp(5));clipChildren=false;clipToPadding=false}
        cardView=WebView(this).apply{
            settings.javaScriptEnabled=true;settings.domStorageEnabled=false;settings.allowFileAccess=true;settings.allowContentAccess=true;settings.loadsImagesAutomatically=true;settings.cacheMode=WebSettings.LOAD_DEFAULT
            addJavascriptInterface(imageBridge,"RovexImages")
            webViewClient=object:WebViewClient(){
                override fun shouldOverrideUrlLoading(view:WebView?,request:android.webkit.WebResourceRequest?):Boolean {
                    val u=request?.url ?: return false
                    if(u.scheme.equals("http",true)||u.scheme.equals("https",true)) {
                        return try { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,u)); true } catch(_:ActivityNotFoundException) { false }
                    }
                    return false
                }
                override fun onRenderProcessGone(view:WebView?,detail:android.webkit.RenderProcessGoneDetail?):Boolean {
                    runCatching { view?.destroy() }; cardViewDestroyed=true
                    if(!isFinishing){ Toast.makeText(this@FlashcardStudyActivity,"The card renderer crashed. Closing this session to keep the app stable.",Toast.LENGTH_LONG).show(); finish() }
                    return true
                }
            }
            setBackgroundColor(ThemeManager.elevated(this@FlashcardStudyActivity))
            setOnTouchListener{_,e->when(e.actionMasked){
                MotionEvent.ACTION_DOWN->{downX=e.rawX;downY=e.rawY;false}
                MotionEvent.ACTION_UP->{
                    val dx=e.rawX-downX;val dy=e.rawY-downY;val now=System.currentTimeMillis()
                    val deliberate=abs(dx)>=dp(72)&&abs(dx)>=abs(dy)*1.25f&&now>=swipeLockedUntil
                    if(deliberate){swipeLockedUntil=now+360L;if(dx>0)previousCard()else nextCard();true}else false
                }
                else->false
            }}
            setOnClickListener{if(!showingBack){showingBack=true;show()}}
        }
        cardFrame.addView(cardView,FrameLayout.LayoutParams(-1,-1))
        bookmark=button("☆",ThemeManager.pastelYellowFill(this)){currentCard()?.let{val active=db.toggleBookmarked(it.id);bookmark.text=if(active)"★"else"☆";styleFlashcardToggle(bookmark,active,"bookmark")}}.apply{contentDescription="Bookmark flashcard"}
        mark=button("⚑",ThemeManager.pastelPinkFill(this)){currentCard()?.let{val active=db.toggleMarked(it.id);mark.text=if(active)"⚑"else"⚐";styleFlashcardToggle(mark,active,"mark")}}.apply{contentDescription="Mark flashcard"}
        cardFrame.addView(bookmark,FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP or Gravity.END).apply{setMargins(0,dp(8),dp(8),0)})
        cardFrame.addView(mark,FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP or Gravity.END).apply{setMargins(0,dp(60),dp(8),0)})
        installMovableToggle(bookmark,"bookmark",0.90f,0.06f,cardFrame)
        installMovableToggle(mark,"mark",0.90f,0.16f,cardFrame)
        root.addView(cardFrame,LinearLayout.LayoutParams(-1,0,1f))

        reveal=button("SHOW ANSWER",Color.rgb(54,116,132)){showingBack=true;show()}
        root.addView(reveal,LinearLayout.LayoutParams(-1,dp(42)).apply{setMargins(dp(10),dp(3),dp(10),dp(3))})

        ratingRow=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),0,dp(10),dp(7));visibility=LinearLayout.GONE}
        val skipRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.END}
        val skip=button("SKIP",Color.rgb(86,96,104)){nextCard()}
        skip.textSize=10f
        skipRow.addView(skip,LinearLayout.LayoutParams(dp(62),dp(30)))
        ratingRow.addView(skipRow,LinearLayout.LayoutParams(-1,dp(32)).apply{setMargins(0,0,0,dp(2))})
        val ratingButtons=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val ratingLabels=arrayOf("AGAIN\n${db.settingInt("learning_again_min",10)}m","HARD\n1d","GOOD\n3d","EASY\n7d")
        val fills=intArrayOf(Color.rgb(178,86,86),Color.rgb(166,130,64),Color.rgb(55,119,135),Color.rgb(108,88,153))
        for(i in 0..3){val r=button(ratingLabels[i],fills[i]){if(it.isEnabled){it.isEnabled=false;rateAndNext(i+1)}};r.textSize=10.5f;ratingButtons.addView(r,LinearLayout.LayoutParams(0,dp(42),1f).apply{if(i>0)setMargins(dp(4),0,0,0)})}
        ratingRow.addView(ratingButtons,LinearLayout.LayoutParams(-1,dp(43)))
        root.addView(ratingRow)
        AdaptiveTypographyManager.apply(root)
        return root
    }

    private data class MenuItemSpec(val title:String,val subtitle:String,val action:()->Unit)

    private fun showVisualMenu(title:String, subtitle:String, items:List<MenuItemSpec>){
        val dialog=Dialog(this)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(14),dp(18),dp(14));background=GradientDrawable().apply{setColor(ThemeManager.dialogBg(this@FlashcardStudyActivity));cornerRadius=dp(24).toFloat()}}
        val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        head.addView(RovexWaveTextView(this@FlashcardStudyActivity).apply{text=title;textSize=21f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(this@FlashcardStudyActivity))},LinearLayout.LayoutParams(0,dp(44),1f))
        head.addView(TextView(this).apply{text="×";textSize=25f;gravity=Gravity.CENTER;setTextColor(ThemeManager.text(this@FlashcardStudyActivity));background=rounded(ThemeManager.elevated(this@FlashcardStudyActivity),14);setOnClickListener{dialog.dismiss()}},LinearLayout.LayoutParams(dp(44),dp(44)))
        root.addView(head)
        root.addView(TextView(this).apply{text=subtitle;textSize=11.5f;setTextColor(ThemeManager.muted(this@FlashcardStudyActivity));setPadding(0,dp(2),0,dp(9))})
        val scroll=ScrollView(this)
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        items.forEach{item->
            val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(12),dp(14),dp(12));isClickable=true;isFocusable=true;background=GradientDrawable().apply{setColor(ThemeManager.elevated(this@FlashcardStudyActivity));cornerRadius=dp(16).toFloat();setStroke(dp(1),android.graphics.Color.argb(45,100,120,140))};setOnClickListener{dialog.dismiss();item.action()}}
            card.addView(TextView(this@FlashcardStudyActivity).apply{text=item.title;textSize=14.5f;typeface=Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(this@FlashcardStudyActivity))})
            card.addView(TextView(this@FlashcardStudyActivity).apply{text=item.subtitle;textSize=11.5f;setTextColor(ThemeManager.muted(this@FlashcardStudyActivity));setPadding(0,dp(4),0,0)})
            AliveMotion.install(card)
            body.addView(card,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(4),0,dp(4))})
        }
        scroll.addView(body);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        dialog.setContentView(root);dialog.window?.setBackgroundDrawableResource(android.R.color.transparent);dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels*.92f).toInt(),(resources.displayMetrics.heightPixels*.78f).toInt())
        AdaptiveTypographyManager.apply(root)
    }

    private fun showReviewerMenu(){
        val c=currentCard()
        showVisualMenu("Study controls","Change the review set or session without leaving your current deck.",listOf(
            MenuItemSpec("Study set / custom study","Choose due, new, hard, failed, bookmarked or marked cards"){chooseMode()},
            MenuItemSpec("Reverse card","Swap front and answer sides"){reverse=!reverse;show()},
            MenuItemSpec("Shuffle: ${if(shuffle)"ON"else"OFF"}","Randomise the next card while keeping your session safe"){shuffle=!shuffle;show()},
            MenuItemSpec("10-card sprint","Restart with a compact focused session"){restartWithLimit(10)},
            MenuItemSpec("25-card sprint","Restart with a medium focused session"){restartWithLimit(25)},
            MenuItemSpec("Session size: ${if(sessionLimit==0)"Unlimited"else sessionLimit}","Choose the maximum cards for this session"){chooseSessionSize()},
            MenuItemSpec("Undo last rating","Revert the most recent SRS rating"){if(lastRatedCardId!=0L&&db.undoLast(lastRatedCardId)){studied=(studied-1).coerceAtLeast(0);lastRatedCardId=0L;Toast.makeText(this,"Last rating undone",Toast.LENGTH_SHORT).show();show()}else Toast.makeText(this,"Nothing to undo",Toast.LENGTH_SHORT).show()},
            MenuItemSpec("Bury until tomorrow","Temporarily remove this card from today's review"){c?.let{db.buryUntilTomorrow(it.id);Toast.makeText(this,"Buried until tomorrow",Toast.LENGTH_SHORT).show();nextCard()}},
            MenuItemSpec("Suspend this card","Keep it out of review until you restore it"){c?.let{db.setSuspended(it.id,true);Toast.makeText(this,"Card suspended",Toast.LENGTH_SHORT).show();nextCard()}},
            MenuItemSpec("End session","Return to the flashcard library"){finish()}
        ))
    }

    private fun chooseMode(){
        val labels=arrayOf("Due now","All cards","New / unseen","Hard","Again / failed","Bookmarked","Marked")
        val values=arrayOf("due","all","unseen","hard","again","bookmarked","marked")
        val selected=values.indexOf(if(mode=="deck")"all"else mode)
        showVisualMenu("Choose study set","Pick the exact card pool for this session.",labels.mapIndexed{i,label->MenuItemSpec(label,if(i==selected)"Currently selected"else "Start this study set"){mode=values[i];position=0;studied=0;showingBack=false;show()}})
    }

    private fun restartWithLimit(limit:Int){
        sessionLimit=limit.coerceAtLeast(1)
        position=0
        studied=0
        showingBack=false
        savePosition()
        show()
    }

    private fun chooseSessionSize(){
        val labels=arrayOf("Unlimited","10 cards","25 cards","50 cards","100 cards");val vals=intArrayOf(0,10,25,50,100)
        showVisualMenu("Session size","Choose a deliberate session boundary.",labels.mapIndexed{i,label->MenuItemSpec(label,if(vals[i]==sessionLimit)"Currently selected"else "Use this session size"){sessionLimit=vals[i];position=0;studied=0;showingBack=false;show()}})
    }

    private fun styleFlashcardToggle(v:TextView, active:Boolean, kind:String){
        val fill=when(kind){
            "bookmark" -> if(active) ThemeManager.pastelYellowFill(this) else ThemeManager.elevated(this)
            else -> if(active) ThemeManager.pastelPinkFill(this) else ThemeManager.elevated(this)
        }
        val fg=when(kind){
            "bookmark" -> if(active) ThemeManager.pastelYellowText(this) else ThemeManager.text(this)
            else -> if(active) ThemeManager.pastelPinkText(this) else ThemeManager.text(this)
        }
        v.setTextColor(fg)
        v.background=GradientDrawable().apply{setColor(fill);cornerRadius=dp(14).toFloat();setStroke(dp(1),Color.argb(if(ThemeManager.isDark(this@FlashcardStudyActivity))70 else 50,100,125,145))}
    }

    private fun installMovableToggle(v:TextView,keyName:String,defaultXFraction:Float,defaultYFraction:Float,parent:FrameLayout){
        val prefs=getPreferences(0)
        var dragging=false; var moved=false; var downX=0f; var downY=0f; var startX=0f; var startY=0f
        fun restorePosition(){
            val savedX=prefs.getFloat("toggle_${keyName}_x",Float.NaN)
            val savedY=prefs.getFloat("toggle_${keyName}_y",Float.NaN)
            val maxX=(parent.width-v.width).coerceAtLeast(0)
            val maxY=(parent.height-v.height).coerceAtLeast(0)
            val point=MovableControlPosition.resolve(savedX,savedY,maxX,maxY,defaultXFraction,defaultYFraction)
            v.x=point.x;v.y=point.y
        }
        parent.post { restorePosition() }
        parent.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_-> if(!dragging) restorePosition() }
        v.setOnTouchListener { view,event ->
            when(event.actionMasked){
                MotionEvent.ACTION_DOWN->{
                    dragging=true;moved=false;downX=event.rawX;downY=event.rawY;startX=view.x;startY=view.y;true
                }
                MotionEvent.ACTION_MOVE->{
                    if(!dragging)return@setOnTouchListener true
                    val dx=event.rawX-downX;val dy=event.rawY-downY
                    if(!moved && kotlin.math.hypot(dx.toDouble(),dy.toDouble())>dp(8))moved=true
                    if(moved){
                        val maxX=(parent.width-view.width).coerceAtLeast(0);val maxY=(parent.height-view.height).coerceAtLeast(0)
                        view.x=(startX+dx).coerceIn(0f,maxX.toFloat());view.y=(startY+dy).coerceIn(0f,maxY.toFloat())
                    };true
                }
                MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{
                    if(moved){
                        val maxX=(parent.width-view.width).coerceAtLeast(1);val maxY=(parent.height-view.height).coerceAtLeast(1)
                        val point=MovableControlPosition.normalized(view.x,view.y,maxX,maxY)
                        prefs.edit().putFloat("toggle_${keyName}_x",point.x).putFloat("toggle_${keyName}_y",point.y).apply()
                    } else if(event.actionMasked==MotionEvent.ACTION_UP)view.performClick()
                    dragging=false;true
                }
                else->true
            }
        }
    }

    private fun ratingName(v:Int)=when(v){1->"Again";2->"Hard";3->"Good";4->"Easy";else->"Not reviewed"}
    private fun updateModeLabel(){val setName=when(mode){"due"->"DUE";"unseen"->"NEW";"hard"->"HARD";"again"->"AGAIN";"bookmarked"->"BOOKMARKED";"marked"->"MARKED";else->"DECK"};val speed=if(elapsedSeconds>0)studied*60L/elapsedSeconds else 0;modeLabel.text="$setName  •  $speed cards/min  •  ${elapsedSeconds/60}m ${elapsedSeconds%60}s"}
    private var ratingInFlight=false
    private fun rateAndNext(rating:Int){
        if(ratingInFlight)return
        val c=currentCard()?:return
        ratingInFlight=true
        try{db.review(c.id,rating);lastRatedCardId=c.id;studied++;elapsedSeconds=(System.currentTimeMillis()-startedAt)/1000L;savePosition();if(sessionLimit>0&&studied>=sessionLimit){finishSession();return};count=currentCount();if(mode=="deck"||mode=="all")position++;if(count<=0){finishSession();return};if(position>=count)position=0;showingBack=false;savePosition();show()}catch(t:Exception){Toast.makeText(this,"Could not save rating: ${t.message ?: t.javaClass.simpleName}",Toast.LENGTH_LONG).show()}finally{ratingInFlight=false}}
    private fun nextCard(){count=currentCount();if(count<=0){finishSession();return};position=if(shuffle&&count>1){var n=(0 until count).random();if(n==position)n=(n+1)%count;n}else(position+1)%count;showingBack=false;savePosition();show()}
    private fun previousCard(){count=currentCount();if(count<=0)return;position=(position-1+count)%count;showingBack=false;savePosition();show()}
    private fun savePosition(durable:Boolean=false){
        val prefs=getPreferences(0)
        // Ordinary UI transitions use apply() to avoid blocking the main thread. Lifecycle
        // boundaries use commit() so the compact recovery cursor is durable before exit.
        prefs.edit()
            .putInt(key("pos"),position)
            .putBoolean(key("back"),showingBack)
            .putInt(key("studied"),studied)
            .putLong(key("elapsed"),elapsedSeconds)
            .let { if(durable) it.commit() else { it.apply(); true } }
    }
    private fun finishSession(){AlertDialog.Builder(this).setTitle("Session complete").setMessage("You reviewed $studied card${if(studied==1)""else"s"}.\n\nTime: ${elapsedSeconds/60}m ${elapsedSeconds%60}s\nSpeed: ${if(elapsedSeconds>0)studied*60L/elapsedSeconds else 0} cards/min").setNegativeButton("Decks"){_,_->finish()}.setPositiveButton("Again"){_,_->position=0;studied=0;showingBack=false;show()}.show()}
    private fun show(){if(!::cardView.isInitialized)return;count=currentCount();if(count<=0){counter.text="0 cards";modeLabel.text="Nothing available in this study set";cardView.loadDataWithBaseURL(null,"<html><body><h2 style='text-align:center;margin-top:35%'>No cards here</h2><p style='text-align:center'>Try another study set from ⋮</p></body></html>","text/html","UTF-8",null);reveal.visibility=LinearLayout.GONE;ratingRow.visibility=LinearLayout.GONE;progress.progress=100;return};if(position>=count)position=0;val c=currentCard()?:return;counter.text=FlashcardProgressLabel.counter(position,count);progress.progress=FlashcardProgressLabel.percentage(position,count);bookmark.text=if(c.bookmarked)"★"else"☆";styleFlashcardToggle(bookmark,c.bookmarked,"bookmark");mark.text=if(c.marked)"⚑"else"⚐";styleFlashcardToggle(mark,c.marked,"mark");val html=if(showingBack xor reverse)c.back else c.front;val dark=ThemeManager.isDark(this); val fg=if(dark)"#F2F5F8"else"#15263B"; val bg=if(dark)"#090909"else"#FFFFFF"; val border=if(dark)"#66788A"else"#8A98A8";val doc="""<html><head><meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=5, user-scalable=yes"><style>html,body{background:$bg!important;color:$fg!important}body{font-family:sans-serif;font-size:19px;line-height:1.58;padding:20px;overflow-wrap:anywhere}img{display:block;max-width:100%;width:auto;height:auto;margin:12px auto;object-fit:contain;cursor:zoom-in}a{color:#73a9ff;text-decoration:underline}table{border-collapse:collapse;max-width:100%;width:auto;display:block;overflow-x:auto}td,th{padding:7px;border:1px solid $border}blockquote{border-left:4px solid #6c8ea5;padding-left:12px}</style><script>document.addEventListener('click',function(e){var i=e.target.closest('img');if(i&&window.RovexImages){window.RovexImages.open(i.src);return;}if(e.target.closest('a'))return;if(window.RovexImages){window.RovexImages.reveal();}});</script></head><body>$html</body></html>""";cardView.alpha=0f; cardView.translationY=dp(6).toFloat()
        cardView.loadDataWithBaseURL("file:///android_asset/",doc,"text/html","UTF-8",null)
        if (AnimationPolicy.enabled(this)) {
            cardView.animate().cancel()
            cardView.animate().alpha(1f).translationY(0f).setDuration(140L).withEndAction { cardView.alpha=1f; cardView.translationY=0f }.start()
        }
        reveal.visibility=if(showingBack)LinearLayout.GONE else LinearLayout.VISIBLE;ratingRow.visibility=if(showingBack)LinearLayout.VISIBLE else LinearLayout.GONE
        for(i in 0 until ratingRow.childCount) ratingRow.getChildAt(i).isEnabled = true
        (ratingRow.getChildAt(1) as? ViewGroup)?.let { group -> for(i in 0 until group.childCount) group.getChildAt(i).isEnabled = true }
        updateModeLabel();savePosition()}
    private fun showImageFullscreen(src:String){
        val dlg=Dialog(this).apply{window?.setBackgroundDrawableResource(android.R.color.black)}
        val web=WebView(this).apply{
            settings.javaScriptEnabled=false;settings.domStorageEnabled=false;settings.allowFileAccess=true;settings.allowContentAccess=true;settings.loadsImagesAutomatically=true
            setBackgroundColor(Color.BLACK)
            webViewClient=object:WebViewClient(){
                override fun onRenderProcessGone(view:WebView?,detail:android.webkit.RenderProcessGoneDetail?):Boolean{runCatching{view?.destroy()};dlg.dismiss();return true}
            }
            setOnClickListener{dlg.dismiss()}
        }
        dlg.setContentView(web);dlg.show();dlg.window?.setLayout(-1,-1)
        val safe=android.text.TextUtils.htmlEncode(src)
        val base=when{src.startsWith("http://",true)||src.startsWith("https://",true)->src.substringBeforeLast('/',src);src.startsWith("file://",true)->src.substringBeforeLast('/',src);else->"file:///android_asset/"}
        val page="<html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=5,user-scalable=yes\"></head><body style=\"margin:0;background:#000;width:100%;height:100vh;display:flex;align-items:center;justify-content:center;overflow:auto;\"><img src=\"$safe\" style=\"max-width:100%;max-height:100%;width:auto;height:auto;object-fit:contain;\"></body></html>"
        web.loadDataWithBaseURL(base,page,"text/html","UTF-8",null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("fc.position", position)
        outState.putBoolean("fc.showingBack", showingBack)
        outState.putInt("fc.studied", studied)
        outState.putLong("fc.elapsedSeconds", elapsedSeconds)
        savePosition(true)
        super.onSaveInstanceState(outState)
    }

    override fun onPause(){elapsedSeconds=(System.currentTimeMillis()-startedAt).coerceAtLeast(0L)/1000L;savePosition(true);timerHandler.removeCallbacks(timerTick);super.onPause()}
    override fun onStop(){elapsedSeconds=(System.currentTimeMillis()-startedAt).coerceAtLeast(0L)/1000L;savePosition(true);super.onStop()}
    override fun onResume(){super.onResume();if(::db.isInitialized&&::cardView.isInitialized){startedAt=System.currentTimeMillis()-elapsedSeconds*1000L;timerHandler.post(timerTick);runCatching{show()}}}
    override fun onDestroy(){timerHandler.removeCallbacks(timerTick);if(::cardView.isInitialized && !cardViewDestroyed){cardViewDestroyed=true;runCatching{cardView.destroy()}};if(::db.isInitialized)db.close();super.onDestroy()}
}
