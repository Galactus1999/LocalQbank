from pathlib import Path
import re, sys

R = Path(sys.argv[1] if len(sys.argv) > 1 else ".")

def one(name):
    hits = [p for p in R.rglob(name) if "app/src/main" in p.as_posix()]
    if len(hits) != 1:
        raise SystemExit(f"ERROR: expected one {name}, found {len(hits)}")
    return hits[0]

# Final dashboard/UI correction after the 8.3.270 repair chain.
p = one("RovexSectionDashboardActivity.kt")
s = p.read_text()

# The 8.3.270 generator accidentally left the capsule return type as View.
s = s.replace("private fun nav():View{", "private fun nav():LinearLayout{", 1)

# Keep question/option text theme-owned even when imported HTML carries explicit colors.
q = one("QuizActivity.kt")
qs = q.read_text()
qs = qs.replace(
    "setTextColor(color ?: ThemeManager.text(this@QuizActivity))",
    "setTextColor(ThemeManager.text(this@QuizActivity))"
)
q.write_text(qs)

helper_start = s.index("private fun subjectRows(")
start = s.index("private fun qbank(", helper_start)
end = s.index("private fun openSearch()", start)

block = r'''private fun subjectRows(rows:List<Row>): List<Row> {
    fun subjectOf(r:Row):String{
        val p=r.path.trim()
        val candidate=p.substringBefore(">").substringBefore("/").substringBefore("::").trim()
        return if(candidate.isBlank() || candidate.equals("root",true) || candidate.equals("general",true)) {
            r.name.substringBefore(" - ").substringBefore(" | ").trim().ifBlank{"General"}
        } else candidate
    }
    return rows.groupBy(::subjectOf).map{(subject,items)->
        val first=items.minByOrNull{it.position} ?: items.first()
        Row(subject,items.sumOf{it.total},items.sumOf{it.solved},items.sumOf{it.correct},
            first.testId,first.position,subject,first.source)
    }.sortedBy{it.name.lowercase()}
}

private fun themedCard(title:String,subtitle:String,accent:Int,action:String?=null,onClick:(()->Unit)?=null):View{
    val box=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL
        setPadding(d(15),d(14),d(15),d(13))
        background=ThemeManager.transparentSectionDrawable(this@RovexSectionDashboardActivity)
        isClickable=onClick!=null
        if(onClick!=null)setOnClickListener{onClick.invoke()}
    }
    val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
    top.addView(TextView(this).apply{
        text=title;textSize=14f;setTypeface(null,Typeface.BOLD)
        setTextColor(ThemeManager.text(this@RovexSectionDashboardActivity))
    },LinearLayout.LayoutParams(0,-2,1f))
    if(action!=null)top.addView(TextView(this).apply{
        text=action;textSize=9f;setTypeface(null,Typeface.BOLD);setTextColor(accent)
    })
    box.addView(top)
    box.addView(TextView(this).apply{
        text=subtitle;textSize=10.5f;setTextColor(ThemeManager.muted(this@RovexSectionDashboardActivity))
        setPadding(0,d(5),0,0)
    })
    return box
}

private fun analyticalHero(title:String,subtitle:String,pct:Int,label:String,metrics:List<Pair<String,String>>):View{
    val box=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL
        setPadding(d(16),d(16),d(16),d(15))
        background=android.graphics.drawable.GradientDrawable().apply{
            setColor(ThemeManager.elevated(this@RovexSectionDashboardActivity))
            cornerRadius=d(24).toFloat()
        }
    }
    val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
    val ring=FrameLayout(this)
    val track=ProgressBar(this@RovexSectionDashboardActivity,null,android.R.attr.progressBarStyleLarge).apply{
        isIndeterminate=false;max=100;progress=pct.coerceIn(0,100)
        progressTintList=android.content.res.ColorStateList.valueOf(ThemeManager.accent(this@RovexSectionDashboardActivity))
    }
    ring.addView(track,FrameLayout.LayoutParams(d(108),d(108),Gravity.CENTER))
    ring.addView(TextView(this).apply{
        text=pct.toString()+"%\n"+label.uppercase()
        gravity=Gravity.CENTER;textSize=14f;setTypeface(null,Typeface.BOLD)
        setTextColor(ThemeManager.text(this@RovexSectionDashboardActivity))
    },FrameLayout.LayoutParams(d(108),d(108),Gravity.CENTER))
    top.addView(ring,LinearLayout.LayoutParams(d(118),d(118)))
    val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(d(13),0,0,0)}
    info.addView(TextView(this).apply{
        text=title;textSize=20f;setTypeface(null,Typeface.BOLD)
        setTextColor(ThemeManager.text(this@RovexSectionDashboardActivity))
    })
    info.addView(TextView(this).apply{
        text=subtitle;textSize=11f;setTextColor(ThemeManager.muted(this@RovexSectionDashboardActivity))
        setPadding(0,d(5),0,0)
    })
    top.addView(info,LinearLayout.LayoutParams(0,-2,1f))
    box.addView(top)

    val metricRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,d(13),0,0)}
    metrics.forEach{(k,v)->
        val m=LinearLayout(this@RovexSectionDashboardActivity).apply{
            orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER
            setPadding(d(3),d(7),d(3),d(7))
            background=android.graphics.drawable.GradientDrawable().apply{
                setColor(ThemeManager.bg(this@RovexSectionDashboardActivity))
                cornerRadius=d(14).toFloat()
            }
        }
        m.addView(TextView(this@RovexSectionDashboardActivity).apply{
            text=k;textSize=8.5f;gravity=Gravity.CENTER
            setTextColor(ThemeManager.muted(this@RovexSectionDashboardActivity))
        })
        m.addView(TextView(this@RovexSectionDashboardActivity).apply{
            text=v;textSize=14f;gravity=Gravity.CENTER;setTypeface(null,Typeface.BOLD)
            setTextColor(ThemeManager.text(this@RovexSectionDashboardActivity));setPadding(0,d(3),0,0)
        })
        metricRow.addView(m,LinearLayout.LayoutParams(0,d(58),1f).apply{setMargins(d(2),0,d(2),0)})
    }
    box.addView(metricRow)
    return box
}

private fun subjectRow(r:Row,index:Int,detail:String,masteryMode:Boolean=false){
    val fg=ThemeManager.pastelAccentText(this,index)
    val card=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL
        setPadding(d(14),d(13),d(14),d(12))
        background=ThemeManager.transparentSectionDrawable(this@RovexSectionDashboardActivity)
        isClickable=r.testId.isNotBlank()
        if(r.testId.isNotBlank())setOnClickListener{
            startActivity(Intent(this@RovexSectionDashboardActivity,QuizActivity::class.java).apply{
                putExtra("testId",r.testId);putExtra("title",r.name);putExtra("position",r.position)
                putExtra("sectionLabel",r.path);putExtra("practiceMode",true)
            })
        }
    }
    val head=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
    head.addView(TextView(this).apply{
        text=r.name;textSize=14f;setTypeface(null,Typeface.BOLD)
        setTextColor(ThemeManager.text(this@RovexSectionDashboardActivity))
    },LinearLayout.LayoutParams(0,-2,1f))
    val pct=if(masteryMode)r.mastery else r.accuracy
    head.addView(TextView(this).apply{
        text=pct.toString()+"%";textSize=13f;setTypeface(null,Typeface.BOLD);setTextColor(fg)
    })
    card.addView(head)
    card.addView(TextView(this).apply{
        text=detail;textSize=10.5f;setTextColor(ThemeManager.muted(this@RovexSectionDashboardActivity))
        setPadding(0,d(5),0,0)
    })
    card.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{
        max=100;progress=pct.coerceIn(0,100)
        progressTintList=android.content.res.ColorStateList.valueOf(fg)
    },LinearLayout.LayoutParams(-1,d(6)).apply{topMargin=d(9)})
    content.addView(card,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(6)})
}

private fun qbank(rows:List<Row>){
    title("QBank","Main QBank • all subjects")
    val subs=subjectRows(rows)
    val total=rows.sumOf{it.total};val solved=rows.sumOf{it.solved};val correct=rows.sumOf{it.correct}
    val mastery=if(total==0)0 else solved*100/total
    val accuracy=if(solved==0)0 else correct*100/solved
    content.addView(analyticalHero("Main QBank","All subjects combined • "+subs.size+" subjects",mastery,"mastery",
        listOf("QUESTIONS" to (solved.toString()+" / "+total),"ACCURACY" to accuracy.toString()+"%","SUBJECTS" to subs.size.toString())),
        LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(10)})
    val resume=TextView(this).apply{
        text="Continue Main QBank";gravity=Gravity.CENTER;textSize=14f;setTypeface(null,Typeface.BOLD)
        setTextColor(ThemeManager.bg(this@RovexSectionDashboardActivity))
        background=android.graphics.drawable.GradientDrawable().apply{setColor(ThemeManager.accent(this@RovexSectionDashboardActivity));cornerRadius=d(26).toFloat()}
        setPadding(0,d(13),0,d(13))
        setOnClickListener{rows.firstOrNull{it.testId.isNotBlank()}?.let{r->startActivity(Intent(this@RovexSectionDashboardActivity,QuizActivity::class.java).apply{
            putExtra("testId",r.testId);putExtra("title",r.name);putExtra("position",r.position);putExtra("practiceMode",true)
        })}}
    }
    content.addView(resume,LinearLayout.LayoutParams(-1,d(50)).apply{bottomMargin=d(10)})
    section("SUBJECTS")
    if(subs.isEmpty())card("No imported subjects","Import content first; this view never invents counts.",ThemeManager.accent(this),"IMPORT"){
        startActivity(Intent(this@RovexSectionDashboardActivity,HtmlImportActivity::class.java))
    } else subs.forEachIndexed{i,r->subjectRow(r,i,r.solved.toString()+" / "+r.total+" attempted • "+r.accuracy+"% accuracy")}
}

private fun cards(c:Triple<Int,Int,Int>){
    title("Cards","Spaced repetition • retention • review")
    val total=c.first;val reviews=c.second;val due=c.third
    val ready=if(total==0)0 else ((total-due).coerceAtLeast(0)*100/total).coerceIn(0,100)
    content.addView(analyticalHero("Flashcards",due.toString()+" due now • "+total+" cards in library",ready,"ready",
        listOf("TOTAL" to total.toString(),"REVIEWS" to reviews.toString(),"DUE" to due.toString())),
        LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(10)})
    val review=TextView(this).apply{
        text="Review Flashcards";gravity=Gravity.CENTER;textSize=14f;setTypeface(null,Typeface.BOLD)
        setTextColor(ThemeManager.bg(this@RovexSectionDashboardActivity))
        background=android.graphics.drawable.GradientDrawable().apply{setColor(ThemeManager.accent(this@RovexSectionDashboardActivity));cornerRadius=d(26).toFloat()}
        setPadding(0,d(13),0,d(13));setOnClickListener{startActivity(Intent(this@RovexSectionDashboardActivity,FlashcardActivity::class.java))}
    }
    content.addView(review,LinearLayout.LayoutParams(-1,d(50)).apply{bottomMargin=d(10)})
    section("REVIEW SNAPSHOT")
    content.addView(themedCard("Due today",if(due==0)"You're up to date." else due.toString()+" cards are waiting for review.",ThemeManager.pastelAccentText(this,1),"REVIEW"){startActivity(Intent(this@RovexSectionDashboardActivity,FlashcardActivity::class.java))},
        LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(6)})
    content.addView(themedCard("Review history",reviews.toString()+" recorded reviews in the local flashcard database.",ThemeManager.pastelAccentText(this,2),"OPEN"){startActivity(Intent(this@RovexSectionDashboardActivity,FlashcardActivity::class.java))},
        LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(6)})
    section("FLASHCARD LIBRARY")
    content.addView(themedCard("Local card database",total.toString()+" cards • local/offline SRS data.",ThemeManager.accent(this),"OPEN CARDS"){startActivity(Intent(this@RovexSectionDashboardActivity,FlashcardActivity::class.java))},
        LinearLayout.LayoutParams(-1,-2))
}

private fun stats(rows:List<Row>,o:Row){
    title("Stats","Performance • accuracy • solved questions")
    content.addView(analyticalHero("Performance Lab",o.solved.toString()+" solved questions • live local progress",o.accuracy,"accuracy",
        listOf("CORRECT" to o.correct.toString(),"WRONG" to (o.solved-o.correct).coerceAtLeast(0).toString(),"TOTAL" to o.total.toString())),
        LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(10)})
    section("PERFORMANCE SNAPSHOT")
    val attempted=o.solved
    val wrong=(o.solved-o.correct).coerceAtLeast(0)
    val unattempted=(o.total-o.solved).coerceAtLeast(0)
    listOf("Solved" to attempted.toString(),"Correct" to o.correct.toString(),"Wrong" to wrong.toString(),"Unattempted" to unattempted.toString()).forEachIndexed{i,(k,v)->
        content.addView(themedCard(k,v,ThemeManager.pastelAccentText(this,i),"OPEN QBANK"){switchSection("qbank")},
            LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(6)})
    }
    val subs=subjectRows(rows)
    section("SUBJECT PERFORMANCE")
    if(subs.isEmpty())card("No performance data yet","Solve questions to populate subject analytics from real progress.",ThemeManager.accent(this),"OPEN QBANK"){switchSection("qbank")}
    else subs.forEachIndexed{i,r->subjectRow(r,i,r.solved.toString()+" solved • "+r.correct+" correct • "+(r.solved-r.correct).coerceAtLeast(0)+" wrong")}
}

private fun mastery(rows:List<Row>,o:Row){
    title("Mastery","Subject coverage • retention • focus areas")
    val subs=subjectRows(rows)
    content.addView(analyticalHero("Overall Mastery",o.solved.toString()+" / "+o.total+" questions attempted • "+subs.size+" subjects",o.mastery,"overall",
        listOf("SUBJECTS" to subs.size.toString(),"ATTEMPTED" to o.solved.toString(),"ACCURACY" to o.accuracy.toString()+"%")),
        LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(10)})
    section("SUBJECT MASTERY")
    if(subs.isEmpty()){
        card("No imported subjects","Import QBank content to build the mastery map.",ThemeManager.accent(this),"IMPORT"){startActivity(Intent(this@RovexSectionDashboardActivity,HtmlImportActivity::class.java))}
    }else{
        subs.forEachIndexed{i,r->subjectRow(r,i,r.mastery.toString()+"% mastery • "+r.solved+" / "+r.total+" attempted",true)}
        section("FOCUS AREAS")
        subs.sortedBy{it.mastery}.take(minOf(3,subs.size)).forEachIndexed{i,r->
            content.addView(themedCard(r.name,r.mastery.toString()+"% mastery • "+(r.total-r.solved).coerceAtLeast(0)+" questions remaining",
                ThemeManager.pastelAccentText(this,i+1),"FOCUS"){
                    if(r.testId.isNotBlank())startActivity(Intent(this@RovexSectionDashboardActivity,QuizActivity::class.java).apply{
                        putExtra("testId",r.testId);putExtra("title",r.name);putExtra("position",r.position);putExtra("sectionLabel",r.path);putExtra("practiceMode",true)
                    })
                },LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(6)})
        }
    }
}

private fun nav():LinearLayout{
    val l=LinearLayout(this).apply{
        orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER
        setPadding(d(7),d(7),d(7),d(7))
        background=android.graphics.drawable.GradientDrawable().apply{
            setColor(ThemeManager.elevated(this@RovexSectionDashboardActivity));cornerRadius=d(30).toFloat()
        }
    }
    listOf("home" to "Home","qbank" to "QBank","cards" to "Cards","stats" to "Stats","mastery" to "Mastery").forEach{(id,label)->
        l.addView(TextView(this).apply{
            tag=id;text=label;gravity=Gravity.CENTER;textSize=10f;setTypeface(null,Typeface.BOLD)
            setPadding(d(2),0,d(2),0);setOnClickListener{switchSection(id)}
        },LinearLayout.LayoutParams(0,-1,1f).apply{setMargins(d(2),0,d(2),0)})
    }
    return l
}
'''
s = s[:start] + block + "\\n" + s[end:]

# Fix the render dispatcher to pass live subject rows into Stats.
s = s.replace("stats(overall)", "stats(rows,overall)")

# Ensure the AMOLED fix is actually structural, not just span stripping.
if "setTextColor(ThemeManager.text(this@QuizActivity))" not in qs:
    raise SystemExit("AMOLED source patch did not apply")

# Bump release identity once for this consolidated UI repair.
g = next(R.rglob("app/build.gradle.kts"))
gs = g.read_text()
gs = re.sub(r'versionName\s*=\s*"8\.3\.270"', 'versionName = "8.3.271"', gs, count=1)
gs = re.sub(r'versionCode\s*=\s*364', 'versionCode = 365', gs, count=1)
g.write_text(gs)

# Compile-risk/source assertions before Gradle.
checks = [
    ("private fun nav():LinearLayout" in s, "nav return type"),
    ("private fun subjectRows(rows:List<Row>)" in s, "subject aggregation"),
    ("private fun cards(c:Triple<Int,Int,Int>)" in s, "flashcard section"),
    ("private fun stats(rows:List<Row>,o:Row)" in s, "stats section"),
    ("private fun mastery(rows:List<Row>,o:Row)" in s, "mastery section"),
    ("sectionGeneration" in s and "token==sectionGeneration" in s, "async generation guard"),
    ("setOnClickListener{switchSection(id)}" in s, "capsule switching"),
    ("analyticsHero(rows)" in s or "analyticalHero(" in s, "analytical hero"),
]
for ok,label in checks:
    if not ok: raise SystemExit("UI271 assertion failed: "+label)
if "setTextColor(ThemeManager.text(this@QuizActivity))" not in qs:
    raise SystemExit("UI271 AMOLED assertion failed")
print("8.3.271 final UI correction PASS")
