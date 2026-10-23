package com.idp.geostampcamera.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.idp.geostampcamera.databinding.FragmentLanguageBinding
import com.idp.geostampcamera.databinding.ItemLanguageBinding
import com.idp.geostampcamera.util.setDebouncedClickListener

class LanguageFragment : Fragment() {

    private var _binding: FragmentLanguageBinding? = null
    private val binding get() = _binding!!

    private data class Option(val code: String, val label: String, val local: String)
    private val options = listOf(
        Option("", "System default", "Default"),
        Option("en", "English", "English"),
        Option("es", "Español", "Spanish"),
        Option("fr", "Français", "French"),
        Option("de", "Deutsch", "German"),
        Option("pt", "Português", "Portuguese"),
        Option("it", "Italiano", "Italian"),
        Option("ru", "Русский", "Russian"),
        Option("ja", "日本語", "Japanese"),
        Option("zh-CN", "中文 (简体)", "Chinese (Simplified)"),
        Option("zh-TW", "中文 (繁體)", "Chinese (Traditional)"),
        Option("ko", "한국어", "Korean"),
        Option("ar", "العربية", "Arabic"),
        Option("in", "Bahasa Indonesia", "Indonesian"),
        Option("tr", "Türkçe", "Turkish"),
        Option("vi", "Tiếng Việt", "Vietnamese"),
        Option("th", "ไทย", "Thai"),
        Option("pl", "Polski", "Polish"),
        Option("nl", "Nederlands", "Dutch"),
        Option("hi", "हिन्दी", "Hindi"),
        Option("bn", "বাংলা", "Bengali"),
        Option("gu", "ગુજરાતી", "Gujarati"),
        Option("kn", "ಕನ್ನಡ", "Kannada"),
        Option("mr", "मराठी", "Marathi"),
        Option("ta", "தமிழ்", "Tamil"),
        Option("te", "తెలుగు", "Telugu"),
        Option("ur", "اردو", "Urdu"),
        Option("uk", "Українська", "Ukrainian"),
        Option("ro", "Română", "Romanian"),
        Option("el", "Ελληνικά", "Greek"),
        Option("cs", "Čeština", "Czech"),
        Option("hu", "Magyar", "Hungarian"),
        Option("sv", "Svenska", "Swedish"),
        Option("da", "Dansk", "Danish"),
        Option("fi", "Suomi", "Finnish"),
        Option("nb", "Norsk Bokmål", "Norwegian"),
        Option("sk", "Slovenčina", "Slovak"),
        Option("bg", "Български", "Bulgarian"),
        Option("hr", "Hrvatski", "Croatian"),
        Option("sr", "Српски", "Serbian"),
        Option("sl", "Slovenščina", "Slovenian"),
        Option("lt", "Lietuvių", "Lithuanian"),
        Option("lv", "Latviešu", "Latvian"),
        Option("et", "Eesti", "Estonian"),
        Option("iw", "עברית", "Hebrew"),
        Option("fa", "فارسی", "Persian"),
        Option("ms", "Bahasa Melayu", "Malay"),
        Option("tl", "Tagalog", "Filipino"),
        Option("sw", "Kiswahili", "Swahili"),
        Option("af", "Afrikaans", "Afrikaans"),
        Option("am", "አማርኛ", "Amharic"),
        Option("ha", "Hausa", "Hausa"),
        Option("yo", "Yorùbá", "Yoruba"),
        Option("zu", "isiZulu", "Zulu"),
        Option("ne", "नेपाली", "Nepali"),
        Option("si", "සිංහල", "Sinhala"),
        Option("km", "ភាសាខ្មែរ", "Khmer"),
        Option("lo", "ພາສາລາວ", "Lao"),
        Option("my", "မြန်မာ", "Burmese"),
        Option("ka", "ქართული", "Georgian"),
        Option("hy", "Հայերեն", "Armenian"),
        Option("az", "Azərbaycan", "Azerbaijani"),
        Option("kk", "Қазақ тілі", "Kazakh"),
        Option("uz", "Oʻzbekcha", "Uzbek"),
        Option("mn", "Монгол", "Mongolian"),
        Option("pa", "ਪੰਜਾਬੀ", "Punjabi"),
        Option("ml", "മലയാളം", "Malayalam"),
        Option("or", "ଓଡ଼ିଆ", "Odia"),
        Option("as", "অসমীয়া", "Assamese"),
        Option("ca", "Català", "Catalan"),
        Option("gl", "Galego", "Galician"),
        Option("eu", "Euskara", "Basque"),
        Option("is", "Íslenska", "Icelandic"),
        Option("sq", "Shqip", "Albanian"),
        Option("mk", "Македонски", "Macedonian"),
        Option("be", "Беларуская", "Belarusian"),
        Option("bs", "Bosanski", "Bosnian"),
        Option("cy", "Cymraeg", "Welsh"),
        Option("ga", "Gaeilge", "Irish"),
        Option("jv", "Basa Jawa", "Javanese"),
        Option("su", "Basa Sunda", "Sundanese"),
        Option("eo", "Esperanto", "Esperanto"),
        Option("mt", "Malti", "Maltese"),
        Option("lb", "Lëtzebuergesch", "Luxembourgish"),
        Option("gd", "Gàidhlig", "Scottish Gaelic"),
        Option("fy", "Frysk", "Frisian"),
        Option("ht", "Kreyòl Ayisyen", "Haitian Creole"),
        Option("mg", "Malagasy", "Malagasy"),
        Option("so", "Soomaali", "Somali"),
        Option("sn", "chiShona", "Shona"),
        Option("st", "Sesotho", "Sesotho"),
        Option("xh", "isiXhosa", "Xhosa"),
        Option("ig", "Asụsụ Igbo", "Igbo"),
        Option("rw", "Ikinyarwanda", "Kinyarwanda"),
        Option("ny", "Chichewa", "Chichewa"),
        Option("om", "Afaan Oromoo", "Oromo"),
        Option("ti", "ትግርኛ", "Tigrinya"),
        Option("tg", "Тоҷикӣ", "Tajik"),
        Option("ky", "Кыргызча", "Kyrgyz"),
        Option("tk", "Türkmen dili", "Turkmen"),
        Option("ps", "پښتو", "Pashto"),
        Option("sd", "سنڌي", "Sindhi"),
        Option("ug", "ئۇيغۇرچە", "Uyghur"),
        Option("bo", "བོད་སྐད།", "Tibetan"),
        Option("ceb", "Cebuano", "Cebuano"),
        Option("haw", "ʻŌlelo Hawaiʻi", "Hawaiian"),
        Option("mi", "Te Reo Māori", "Maori"),
        Option("sm", "Gagana Samoa", "Samoan"),
        Option("la", "Latina", "Latin"),
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLanguageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.languageToolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.languageList.layoutManager = LinearLayoutManager(requireContext())
        binding.languageList.adapter = Adapter()
    }
    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        com.idp.geostampcamera.ui.common.AdBanner.install(this)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(val b: ItemLanguageBinding) : RecyclerView.ViewHolder(b.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemLanguageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun getItemCount(): Int = options.size
        override fun onBindViewHolder(holder: VH, position: Int) {
            val opt = options[position]
            holder.b.langPrimary.text = opt.label
            holder.b.langSecondary.text = opt.local
            holder.b.root.setDebouncedClickListener {
                val locales = if (opt.code.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                else LocaleListCompat.forLanguageTags(opt.code)
                AppCompatDelegate.setApplicationLocales(locales)
                findNavController().popBackStack()
            }
        }
    }
}
