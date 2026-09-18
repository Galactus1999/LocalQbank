package com.localqbank.library

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** In-app Google evidence surface. Search stays in Rovex; foreign result pages can open externally. */
class GoogleSearchActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private val trustedHosts = setOf(
        "google.com", "www.google.com", "google.co.in", "www.google.co.in",
        "consent.google.com", "accounts.google.com", "gstatic.com", "www.gstatic.com",
        "googleusercontent.com", "www.googleusercontent.com"
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val query=intent.getStringExtra("query").orEmpty().trim().ifBlank{"medical study question"}
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=ThemeManager.backgroundDrawable(this@GoogleSearchActivity)}
        val questionId = intent.getLongExtra("questionId", -1L)
        val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(5),dp(8),dp(5))}
        bar.addView(TextView(this).apply{text="‹";textSize=30f;gravity=Gravity.CENTER;setTextColor(ThemeManager.text(this@GoogleSearchActivity));setOnClickListener{finish()}},LinearLayout.LayoutParams(dp(44),dp(42)))
        bar.addView(TextView(this).apply{text="GOOGLE SEARCH • ROVEX";textSize=15f;typeface=android.graphics.Typeface.DEFAULT_BOLD;setTextColor(ThemeManager.text(this@GoogleSearchActivity))},LinearLayout.LayoutParams(0,dp(42),1f))
        bar.addView(TextView(this).apply{text="SAVE";textSize=11f;typeface=android.graphics.Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER;setTextColor(ThemeManager.text(this@GoogleSearchActivity));setPadding(dp(10),0,dp(10),0);setOnClickListener{
            if(questionId<=0L){android.widget.Toast.makeText(this@GoogleSearchActivity,"Open Google Search from a question to save it to that question's Notes",android.widget.Toast.LENGTH_LONG).show();return@setOnClickListener}
            val query=intent.getStringExtra("query").orEmpty(); val ok=AppManagers.knowledge.saveRenderedViewWithNote(web,questionId,"Google Search • Rovex","Search query: $query\n\nSaved screen from Google Search.")
            android.widget.Toast.makeText(this@GoogleSearchActivity,if(ok)"Google search saved to Notes" else "Could not save search",android.widget.Toast.LENGTH_SHORT).show()
        }},LinearLayout.LayoutParams(dp(64),dp(42)))
        root.addView(bar)
        web=WebView(this).apply{
            setBackgroundColor(if(ThemeManager.get(this@GoogleSearchActivity)==ThemeManager.AMOLED)Color.BLACK else Color.WHITE)
            settings.javaScriptEnabled=true // Google Search requires JS for its current result UI.
            settings.domStorageEnabled=true
            settings.loadsImagesAutomatically=true
            settings.mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.allowFileAccess=false
            settings.allowContentAccess=false
            settings.userAgentString=settings.userAgentString+" RovexGoogleSearch/2.0"
            webViewClient=object:WebViewClient(){
                override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                    val uri=request.url
                    if (uri.scheme.equals("https",true) && isTrustedGoogleHost(uri.host.orEmpty())) return false
                    if (uri.scheme.equals("http",true) || uri.scheme.equals("https",true)) {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW,uri)) }
                        return true
                    }
                    return true
                }
            }
            webChromeClient=WebChromeClient()
        }
        root.addView(web,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
        web.loadUrl("https://www.google.com/search?q="+java.net.URLEncoder.encode(query,"UTF-8"))
    }

    private fun isTrustedGoogleHost(host:String):Boolean {
        val normalized=host.lowercase().trim('.')
        return trustedHosts.any{normalized==it || normalized.endsWith(".$it")}
    }

    override fun onBackPressed(){if(::web.isInitialized&&web.canGoBack())web.goBack()else super.onBackPressed()}
    override fun onDestroy(){if(::web.isInitialized){web.stopLoading();web.loadUrl("about:blank");web.removeAllViews();web.destroy()};super.onDestroy()}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
}
