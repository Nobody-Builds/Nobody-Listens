package com.nobodybuilds.listens.utils

import android.os.Build
import com.atilika.kuromoji.ipadic.Tokenizer
import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType

// Roman Multilang 
object MultiLangRomanizer {
    private val kuromojiTokenizer: Tokenizer? by lazy {
        try {
            Thread.currentThread().contextClassLoader = MultiLangRomanizer::class.java.classLoader
            Tokenizer()
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    private val HANGUL_ROMAJA_MAP = mapOf(
        "cho" to mapOf("ᄀ" to "g", "ᄁ" to "kk", "ᄂ" to "n", "ᄃ" to "d", "ᄄ" to "tt", "ᄅ" to "r", "ᄆ" to "m", "ᄇ" to "b", "ᄈ" to "pp", "ᄉ" to "s", "ᄊ" to "ss", "ᄋ" to "", "ᄌ" to "j", "ᄍ" to "jj", "ᄎ" to "ch", "ᄏ" to "k", "ᄐ" to "t", "ᄑ" to "p", "ᄒ" to "h"),
        "jung" to mapOf("ᅡ" to "a", "ᅢ" to "ae", "ᅣ" to "ya", "ᅤ" to "yae", "ᅥ" to "eo", "ᅦ" to "e", "ᅧ" to "yeo", "ᅨ" to "ye", "ᅩ" to "o", "ᅪ" to "wa", "ᅫ" to "wae", "ᅬ" to "oe", "ᅭ" to "yo", "ᅮ" to "u", "ᅯ" to "wo", "ᅰ" to "we", "ᅱ" to "wi", "ᅲ" to "yu", "ᅳ" to "eu", "ᅴ" to "eui", "ᅵ" to "i"),
        "jong" to mapOf("ᆨ" to "k", "ᆨᄋ" to "g", "ᆨᄂ" to "ngn", "ᆨᄅ" to "ngn", "ᆨᄆ" to "ngm", "ᆨᄒ" to "kh", "ᆩ" to "kk", "ᆩᄋ" to "kg", "ᆩᄂ" to "ngn", "ᆩᄅ" to "ngn", "ᆩᄆ" to "ngm", "ᆩᄒ" to "kh", "ᆪ" to "k", "ᆪᄋ" to "ks", "ᆪᄂ" to "ngn", "ᆪᄅ" to "ngn", "ᆪᄆ" to "ngm", "ᆪᄒ" to "kch", "ᆫ" to "n", "ᆫᄅ" to "ll", "ᆬ" to "n", "ᆬᄋ" to "nj", "ᆬᄂ" to "nn", "ᆬᄅ" to "nn", "ᆬᄆ" to "nm", "ᆬㅎ" to "nch", "ᆭ" to "n", "ᆭᄋ" to "nh", "ᆭᄅ" to "nn", "ᆮ" to "t", "ᆮᄋ" to "d", "ᆮᄂ" to "nn", "ᆮᄅ" to "nn", "ᆮᄆ" to "nm", "ᆮᄒ" to "th", "ᆯ" to "l", "ᆯᄋ" to "r", "ᆯᄂ" to "ll", "ᆯᄅ" to "ll", "ᆰ" to "k", "ᆰᄋ" to "lg", "ᆰᄂ" to "ngn", "ᆰᄅ" to "ngn", "ᆰᄆ" to "ngm", "ᆰᄒ" to "lkh", "ᆱ" to "m", "ᆱᄋ" to "lm", "ᆱᄂ" to "mn", "ᆱᄅ" to "mn", "ᆱᄆ" to "mm", "ᆱᄒ" to "lmh", "ᆲ" to "p", "ᆲᄋ" to "lb", "ᆲᄂ" to "mn", "ᆲᄅ" to "mn", "ᆲᄆ" to "mm", "ᆲᄒ" to "lph", "ᆳ" to "t", "ᆳᄋ" to "ls", "ᆳᄂ" to "nn", "ᆳᄅ" to "nn", "ᆳᄆ" to "nm", "ᆳᄒ" to "lsh", "ᆴ" to "t", "ᆴᄋ" to "lt", "ᆴᄂ" to "nn", "ᆴᄅ" to "nn", "ᆴᄆ" to "nm", "ᆴᄒ" to "lth", "ᆵ" to "p", "ᆵᄋ" to "lp", "ᆵᄂ" to "mn", "ᆵᄅ" to "mn", "ᆵᄆ" to "mm", "ᆵᄒ" to "lph", "ᆶ" to "l", "ᆶᄋ" to "lh", "ᆶᄂ" to "ll", "ᆶᄅ" to "ll", "ᆶᄆ" to "lm", "ᆶᄒ" to "lh", "ᆷ" to "m", "ᆷᄅ" to "mn", "ᆸ" to "p", "ᆸᄋ" to "b", "ᆸᄂ" to "mn", "ᆸᄅ" to "mn", "ᆸᄆ" to "mm", "ᆸᄒ" to "ph", "ᆹ" to "p", "ᆹᄋ" to "ps", "ᆹᄂ" to "mn", "ᆹᄅ" to "mn", "ᆹᄆ" to "mm", "ᆹᄒ" to "psh", "ᆺ" to "t", "ᆺᄋ" to "s", "ᆺᄂ" to "nn", "ᆺᄅ" to "nn", "ᆺᄆ" to "nm", "ᆺᄒ" to "sh", "ᆻ" to "t", "ᆻᄋ" to "ss", "ᆻᄂ" to "tn", "ᆻᄅ" to "tn", "ᆻᄆ" to "nm", "ᆻᄒ" to "th", "ᆼ" to "ng", "ᆽ" to "t", "ᆽᄋ" to "j", "ᆽᄂ" to "nn", "ᆽᄅ" to "nn", "ᆽᄆ" to "nm", "ᆽᄒ" to "ch", "ᆾ" to "t", "ᆾᄋ" to "ch", "ᆾᄂ" to "nn", "ᆾᄅ" to "nn", "ᆾᄆ" to "nm", "ᆾᄒ" to "ch", "ᆿ" to "k", "ᆿᄋ" to "k", "ᆿᄂ" to "ngn", "ᆿᄅ" to "ngn", "ᆿᄆ" to "ngm", "ᆿᄒ" to "kh", "ᇀ" to "t", "ᇀᄋ" to "t", "ᇀᄂ" to "nn", "ᇀᄅ" to "nn", "ᇀᄆ" to "nm", "ᇀᄒ" to "th", "ᇁ" to "p", "ᇁᄋ" to "p", "ᇁᄂ" to "mn", "ᇁᄅ" to "mn", "ᇁᄆ" to "mm", "ᇁᄒ" to "ph", "ᇂ" to "t", "ᇂᄋ" to "h", "ᇂᄂ" to "nn", "ᇂᄅ" to "nn", "ᇂᄆ" to "mm", "ᇂᄒ" to "t", "ᇂᄀ" to "k")
    )

    private val DEVANAGARI_ROMAJI_MAP = mapOf(
        "अ" to "a", "आ" to "aa", "इ" to "i", "ई" to "ee", "उ" to "u", "ऊ" to "oo", "ऋ" to "ri", "ए" to "e", "ऐ" to "ai", "ओ" to "o", "औ" to "au", "क" to "k", "ख" to "kh", "ग" to "g", "घ" to "gh", "ङ" to "ng", "च" to "ch", "छ" to "chh", "ज" to "j", "झ" to "jh", "ञ" to "ny", "ट" to "t", "ठ" to "th", "ड" to "d", "ढ" to "dh", "ण" to "n", "त" to "t", "थ" to "th", "द" to "d", "ध" to "dh", "न" to "n", "प" to "p", "फ" to "ph", "ब" to "b", "भ" to "bh", "म" to "m", "य" to "y", "र" to "r", "ल" to "l", "व" to "v", "श" to "sh", "ष" to "sh", "स" to "s", "ह" to "h", "क्ष" to "ksh", "त्र" to "tr", "ज्ञ" to "gy", "श्र" to "shr", "ा" to "aa", "ि" to "i", "ी" to "ee", "ु" to "u", "ू" to "oo", "ृ" to "ri", "े" to "e", "ै" to "ai", "ो" to "o", "ौ" to "au", "ं" to "n", "ः" to "h", "ँ" to "n", "़" to "", "्" to "", "०" to "0", "१" to "1", "२" to "2", "३" to "3", "४" to "4", "५" to "5", "६" to "6", "७" to "7", "८" to "8", "९" to "9", "ॐ" to "Om", "ऽ" to "", "क़" to "q", "ख़" to "kh", "ग़" to "g", "ज़" to "z", "ड़" to "r", "ढ़" to "rh", "फ़" to "f", "य़" to "y", "क\u093C" to "q", "ख\u093C" to "kh", "ग\u093C" to "g", "ज\u093C" to "z", "ड\u093C" to "r", "ढ\u093C" to "rh", "फ\u093C" to "f", "य\u093C" to "y"
    )

    private val GURMUKHI_ROMAJI_MAP = mapOf(
        "ੳ" to "o", "ਅ" to "a", "ੲ" to "e", "ਸ" to "s", "ਹ" to "h", "ਕ" to "k", "ਖ" to "kh", "ਗ" to "g", "ਘ" to "gh", "ਙ" to "ng", "ਚ" to "ch", "ਛ" to "chh", "ਜ" to "j", "ਝ" to "jh", "ਞ" to "ny", "ਟ" to "t", "ਠ" to "th", "ਡ" to "d", "ਢ" to "dh", "ਣ" to "n", "ਤ" to "t", "ਥ" to "th", "ਦ" to "d", "ਧ" to "dh", "ਨ" to "n", "ਪ" to "p", "ਫ" to "ph", "ਬ" to "b", "ਭ" to "bh", "ਮ" to "m", "ਯ" to "y", "ਰ" to "r", "ਲ" to "l", "ਵ" to "v", "ੜ" to "r", "ਸ਼" to "sh", "ਖ਼" to "kh", "ਗ਼" to "g", "ਜ਼" to "z", "ਫ਼" to "f", "ਲ਼" to "l", "ਾ" to "aa", "ਿ" to "i", "ੀ" to "ee", "ੁ" to "u", "ੂ" to "oo", "ੇ" to "e", "ੈ" to "ai", "ੋ" to "o", "ੌ" to "au", "ੰ" to "n", "ਂ" to "n", "ੱ" to "", "੍" to "", "਼" to "", "ੴ" to "Ek Onkar", "੦" to "0", "੧" to "1", "੨" to "2", "੩" to "3", "੪" to "4", "੫" to "5", "੬" to "6", "੭" to "7", "੮" to "8", "੯" to "9"
    )

    private val GENERAL_CYRILLIC_ROMAJI_MAP = mapOf(
        "А" to "A", "Б" to "B", "В" to "V", "Г" to "G", "Ґ" to "G", "Д" to "D", "Ѓ" to "Ǵ", "Ђ" to "Đ", "Е" to "E", "Ё" to "Yo", "Є" to "Ye", "Ж" to "Zh", "З" to "Z", "Ѕ" to "Dz", "И" to "I", "І" to "I", "Ї" to "Yi", "Й" to "Y", "Ј" to "Y", "К" to "K", "Л" to "L", "Љ" to "Ly", "М" to "M", "Н" to "N", "Њ" to "Ny", "О" to "O", "П" to "P", "Р" to "R", "С" to "S", "Т" to "T", "Ћ" to "Ć", "У" to "U", "Ў" to "Ŭ", "Ф" to "F", "Х" to "Kh", "Ц" to "Ts", "Ч" to "Ch", "Џ" to "Dž", "Ш" to "Sh", "Щ" to "Shch", "Ъ" to "ʺ", "Ы" to "Y", "Ь" to "ʹ", "Э" to "E", "Ю" to "Yu", "Я" to "Ya",
        "а" to "a", "б" to "b", "в" to "v", "г" to "g", "ґ" to "g", "д" to "d", "ѓ" to "ǵ", "ђ" to "đ", "е" to "e", "ё" to "yo", "є" to "ye", "ж" to "zh", "з" to "z", "ѕ" to "dz", "и" to "i", "і" to "i", "ї" to "yi", "й" to "y", "ј" to "y", "к" to "k", "л" to "l", "љ" to "ly", "м" to "m", "н" to "n", "њ" to "ny", "о" to "o", "п" to "p", "р" to "r", "с" to "s", "т" to "t", "ћ" to "ć", "у" to "u", "ў" to "ŭ", "ф" to "f", "х" to "kh", "ц" to "ts", "ч" to "ch", "џ" to "dž", "ш" to "sh", "щ" to "shch", "ъ" to "ʺ", "ы" to "y", "ь" to "ʹ", "э" to "e", "ю" to "yu", "я" to "ya"
    )

    private val RUSSIAN_ROMAJI_MAP = mapOf("ого" to "ovo", "Ого" to "Ovo", "его" to "evo", "Его" to "Evo")
    private val UKRAINIAN_ROMAJI_MAP = mapOf("Г" to "H", "г" to "h", "Ґ" to "G", "ґ" to "g", "Є" to "Ye", "є" to "ye", "І" to "I", "і" to "i", "Ї" to "Yi", "ї" to "yi")
    private val SERBIAN_ROMAJI_MAP = mapOf("Ж" to "Ž", "Љ" to "Lj", "Њ" to "Nj", "Ц" to "C", "Ч" to "Č", "Џ" to "Dž", "Ш" to "Š", "Х" to "H", "ж" to "ž", "љ" to "lj", "њ" to "nj", "ц" to "c", "ч" to "č", "џ" to "dž", "ш" to "š", "х" to "h")
    private val BULGARIAN_ROMAJI_MAP = mapOf("Ж" to "Zh", "Ц" to "Ts", "Ч" to "Ch", "Ш" to "Sh", "Щ" to "Sht", "Ъ" to "A", "Ь" to "Y", "Ю" to "Yu", "Я" to "Ya", "ж" to "zh", "ц" to "ts", "ч" to "ch", "ш" to "sh", "щ" to "sht", "ъ" to "a", "ь" to "y", "ю" to "yu", "я" to "ya")
    private val BELARUSIAN_ROMAJI_MAP = mapOf("Г" to "H", "г" to "h", "Ў" to "W", "ў" to "w")
    private val KYRGYZ_ROMAJI_MAP = mapOf("Ү" to "Ü", "ү" to "ü", "Ы" to "Y", "ы" to "y")
    private val MACEDONIAN_ROMAJI_MAP = mapOf("Ѓ" to "Gj", "Ѕ" to "Dz", "И" to "I", "Ј" to "J", "Љ" to "Lj", "Њ" to "Nj", "Ќ" to "Kj", "Џ" to "Dž", "Ч" to "Č", "Ш" to "Sh", "Ж" to "Zh", "Ц" to "C", "Х" to "H", "ѓ" to "gj", "ѕ" to "dz", "и" to "i", "ј" to "j", "љ" to "lj", "њ" to "nj", "ќ" to "kj", "џ" to "dž", "ч" to "č", "ш" to "sh", "ж" to "zh", "ц" to "c", "х" to "h")

    private val RUSSIAN_CYRILLIC_LETTERS = setOf("А", "Б", "В", "Г", "Д", "Е", "Ё", "Ж", "З", "И", "Й", "К", "Л", "М", "Н", "О", "П", "Р", "С", "Т", "У", "Ф", "Х", "Ц", "Ч", "Ш", "Щ", "Ъ", "Ы", "Ь", "Э", "Ю", "Я", "а", "б", "в", "г", "д", "е", "ё", "ж", "з", "и", "й", "к", "л", "м", "н", "о", "п", "р", "с", "т", "у", "ф", "х", "ц", "ч", "ш", "щ", "ъ", "ы", "ь", "э", "ю", "я")
    private val UKRAINIAN_CYRILLIC_LETTERS = setOf("А", "Б", "В", "Г", "Ґ", "Д", "Е", "Є", "Ж", "З", "И", "І", "Ї", "Й", "К", "Л", "М", "Н", "О", "П", "Р", "С", "Т", "У", "Ф", "Х", "Ц", "Ч", "Ш", "Щ", "Ь", "Ю", "Я", "а", "б", "в", "г", "ґ", "д", "е", "є", "ж", "з", "и", "і", "ї", "й", "к", "л", "м", "н", "о", "п", "р", "с", "т", "у", "ф", "х", "ц", "ч", "ш", "щ", "ь", "ю", "я")
    private val SERBIAN_CYRILLIC_LETTERS = setOf("А", "Б", "В", "Г", "Д", "Ђ", "Е", "Ж", "З", "И", "Ј", "К", "Л", "Љ", "М", "Н", "Њ", "О", "П", "Р", "С", "Т", "Ћ", "У", "Ф", "Х", "Ц", "Ч", "Џ", "Ш", "а", "б", "в", "г", "д", "ђ", "е", "ж", "з", "и", "ј", "к", "л", "љ", "м", "н", "њ", "о", "п", "р", "с", "т", "ћ", "у", "ф", "х", "ц", "ч", "џ", "ш")
    private val BULGARIAN_CYRILLIC_LETTERS = setOf("А", "Б", "В", "Г", "Д", "Е", "Ж", "З", "И", "Й", "К", "Л", "М", "Н", "О", "П", "Р", "С", "Т", "У", "Ф", "Х", "Ц", "Ч", "Ш", "Щ", "Ъ", "Ь", "Ю", "Я", "а", "б", "в", "г", "д", "е", "ж", "з", "и", "й", "к", "л", "м", "н", "о", "п", "р", "с", "т", "у", "ф", "х", "ц", "ч", "ш", "щ", "ъ", "ь", "ю", "я")
    private val BELARUSIAN_CYRILLIC_LETTERS = setOf("А", "Б", "В", "Г", "Д", "Е", "Ё", "Ж", "З", "І", "Й", "К", "Л", "М", "Н", "О", "П", "Р", "С", "Т", "У", "Ў", "Ф", "Х", "Ц", "Ч", "Ш", "Ь", "Ю", "Я", "Ы", "Э", "а", "б", "в", "г", "д", "е", "ё", "ж", "з", "і", "й", "к", "л", "м", "н", "о", "п", "р", "с", "т", "у", "ў", "ф", "х", "ц", "ч", "ш", "ь", "ю", "я", "ы", "э")
    private val KYRGYZ_CYRILLIC_LETTERS = setOf("А", "Б", "В", "Г", "Д", "Е", "Ё", "Ж", "З", "И", "Й", "К", "Л", "М", "Н", "Ң", "О", "Ө", "П", "Р", "С", "Т", "У", "Ү", "Ф", "Х", "Ц", "Ч", "Ш", "Щ", "Ъ", "Ы", "Ь", "Э", "Ю", "Я", "а", "б", "в", "г", "д", "е", "ё", "ж", "з", "и", "й", "к", "л", "м", "н", "ң", "о", "ө", "п", "р", "с", "т", "у", "ү", "ф", "х", "ц", "ч", "ш", "щ", "ъ", "ы", "ь", "э", "ю", "я")
    private val MACEDONIAN_CYRILLIC_LETTERS = setOf("А", "Б", "В", "Г", "Д", "Ѓ", "Е", "Ж", "З", "Ѕ", "И", "Ј", "К", "Л", "Љ", "М", "Н", "Њ", "О", "П", "Р", "С", "Т", "Ќ", "У", "Ф", "Х", "Ц", "Ч", "Џ", "Ш", "а", "б", "в", "г", "д", "ѓ", "е", "ж", "з", "ѕ", "и", "ј", "к", "л", "љ", "м", "н", "њ", "о", "п", "р", "с", "т", "ќ", "у", "ф", "х", "ц", "ч", "џ", "ш")

    private val UKRAINIAN_SPECIFIC_CYRILLIC_LETTERS = setOf("Ґ", "ґ", "Є", "є", "І", "і", "Ї", "ї")
    private val SERBIAN_SPECIFIC_CYRILLIC_LETTERS = setOf("Ђ", "ђ", "Ј", "ј", "Љ", "љ", "Њ", "њ", "Ћ", "ћ", "Џ", "џ")
    private val BELARUSIAN_SPECIFIC_CYRILLIC_LETTERS = setOf("Ў", "ў", "І", "і")
    private val KYRGYZ_SPECIFIC_CYRILLIC_LETTERS = setOf("Ң", "ң", "Ө", "ө", "Ү", "ү")
    private val MACEDONIAN_SPECIFIC_CYRILLIC_LETTERS = setOf("Ѓ", "ѓ", "Ѕ", "ѕ", "Ќ", "ќ")

    fun isJapanese(text: String): Boolean {
        // Detect Hiragana or Katakana
        if (text.any { it in '\u3040'..'\u309F' || it in '\u30A0'..'\u30FF' }) return true
        
        // Treat Kanji-only lines as Japanese (prioritized over Chinese in the main parsing loop)
        return text.any { it in '\u4E00'..'\u9FFF' }
    }
    fun isKorean(text: String) = text.any { it in '\uAC00'..'\uD7A3' }
    fun isHindi(text: String) = text.any { it in '\u0900'..'\u097F' }
    fun isPunjabi(text: String) = text.any { it in '\u0A00'..'\u0A7F' }
    fun isCyrillic(text: String) = text.any { it in '\u0400'..'\u04FF' }
    fun isChinese(text: String) = text.any { it in '\u4E00'..'\u9FFF' }

    fun isScriptThatNeedsRomanization(text: String): Boolean {
        return text.any { char ->
            val code = char.code
            code in 0x3040..0x309F || // Hiragana
            code in 0x30A0..0x30FF || // Katakana
            code in 0x4E00..0x9FFF || // CJK Unified Ideographs (Chinese)
            code in 0xAC00..0xD7A3 || // Hangul (Korean)
            code in 0x0900..0x097F || // Devanagari (Hindi)
            code in 0x0A00..0x0A7F || // Gurmukhi (Punjabi)
            code in 0x0400..0x04FF    // Cyrillic
        }
    }

    private fun isRussian(text: String) = text.any { RUSSIAN_CYRILLIC_LETTERS.contains(it.toString()) } && text.all { RUSSIAN_CYRILLIC_LETTERS.contains(it.toString()) || !it.toString().matches("[\\u0400-\\u04FF]".toRegex()) }
    private fun isUkrainian(text: String) = text.any { UKRAINIAN_CYRILLIC_LETTERS.contains(it.toString()) || UKRAINIAN_SPECIFIC_CYRILLIC_LETTERS.contains(it.toString()) } && text.all { UKRAINIAN_CYRILLIC_LETTERS.contains(it.toString()) || UKRAINIAN_SPECIFIC_CYRILLIC_LETTERS.contains(it.toString()) || !it.toString().matches("[\\u0400-\\u04FF]".toRegex()) }
    private fun isSerbian(text: String) = text.any { SERBIAN_CYRILLIC_LETTERS.contains(it.toString()) || SERBIAN_SPECIFIC_CYRILLIC_LETTERS.contains(it.toString()) } && text.all { SERBIAN_CYRILLIC_LETTERS.contains(it.toString()) || SERBIAN_SPECIFIC_CYRILLIC_LETTERS.contains(it.toString()) || !it.toString().matches("[\\u0400-\\u04FF]".toRegex()) }
    private fun isBulgarian(text: String) = text.any { BULGARIAN_CYRILLIC_LETTERS.contains(it.toString()) } && text.all { BULGARIAN_CYRILLIC_LETTERS.contains(it.toString()) || !it.toString().matches("[\\u0400-\\u04FF]".toRegex()) }
    private fun isBelarusian(text: String) = text.any { BELARUSIAN_CYRILLIC_LETTERS.contains(it.toString()) || BELARUSIAN_SPECIFIC_CYRILLIC_LETTERS.contains(it.toString()) } && text.all { BELARUSIAN_CYRILLIC_LETTERS.contains(it.toString()) || BELARUSIAN_SPECIFIC_CYRILLIC_LETTERS.contains(it.toString()) || !it.toString().matches("[\\u0400-\\u04FF]".toRegex()) }
    private fun isKyrgyz(text: String) = text.any { KYRGYZ_CYRILLIC_LETTERS.contains(it.toString()) || KYRGYZ_SPECIFIC_CYRILLIC_LETTERS.contains(it.toString()) } && text.all { KYRGYZ_CYRILLIC_LETTERS.contains(it.toString()) || KYRGYZ_SPECIFIC_CYRILLIC_LETTERS.contains(it.toString()) || !it.toString().matches("[\\u0400-\\u04FF]".toRegex()) }
    private fun isMacedonian(text: String) = text.any { MACEDONIAN_CYRILLIC_LETTERS.contains(it.toString()) || MACEDONIAN_SPECIFIC_CYRILLIC_LETTERS.contains(it.toString()) } && text.all { MACEDONIAN_CYRILLIC_LETTERS.contains(it.toString()) || MACEDONIAN_SPECIFIC_CYRILLIC_LETTERS.contains(it.toString()) || !it.toString().matches("[\\u0400-\\u04FF]".toRegex()) }

    fun romanizeJapanese(japaneseText: String): String? {
        val tokenizer = kuromojiTokenizer ?: return null

        return try {
            val tokens = tokenizer.tokenize(japaneseText)
            val katakanaBuilder = StringBuilder()

            // Handle irregular readings and token merging
            val processedReadings = mutableListOf<Triple<String, String, String>>() // <Reading, POS1, POS2>
            var i = 0
            while (i < tokens.size) {
                val token = tokens[i]
                val nextToken = if (i + 1 < tokens.size) tokens[i + 1] else null
                
                // Merge irregular counts (e.g., hitori, futari)
                if (token.surface == "一" && nextToken?.surface == "人") {
                    processedReadings.add(Triple("ヒトリ", "名詞", "一般"))
                    i += 2
                    continue
                }
                if (token.surface == "二" && nextToken?.surface == "人") {
                    processedReadings.add(Triple("フタリ", "名詞", "一般"))
                    i += 2
                    continue
                }
                
                val reading = token.reading
                val surface = token.surface
                val pos1 = token.partOfSpeechLevel1
                val pos2 = token.partOfSpeechLevel2

                // Handle special particle pronunciations (kunyomi vs particles)
                val readingText = if (pos1 == "助詞") {
                    when (surface) {
                        "は" -> "ワ"
                        "へ" -> "エ"
                        "を" -> "オ"
                        else -> if (!reading.isNullOrBlank() && reading != "*") reading else surface
                    }
                } else {
                    if (!reading.isNullOrBlank() && reading != "*") reading else surface
                }
                processedReadings.add(Triple(readingText, pos1, pos2))
                i++
            }

            processedReadings.forEachIndexed { index, pair ->
                val readingText = pair.first
                val pos1 = pair.second
                val pos2 = pair.third

                val prevReading = if (index > 0) processedReadings[index - 1].first else ""
                val prevEndsWithSokuon = prevReading.endsWith("ッ")
                
                // Intelligent spacing:
                // - Join inflections (after sokuon 'ッ') and auxiliaries (-ta, -masu)
                // - Add spaces for readability before particles and content words
                val needsNoSpace = index == 0 || prevEndsWithSokuon || pos1 == "助動詞" || pos2 == "接尾" || pos1 == "記号"

                if (!needsNoSpace) {
                    katakanaBuilder.append(" ")
                }
                katakanaBuilder.append(readingText)
            }

            val katakanaText = katakanaBuilder.toString().replace("\\s+".toRegex(), " ").trim()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val transliterator = android.icu.text.Transliterator.getInstance("Katakana-Latin; Lower")
                transliterator.transliterate(katakanaText)
            } else {
                katakanaText
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    fun romanizeChinese(text: String): String? {
        return try {
            val format = HanyuPinyinOutputFormat().apply {
                caseType = HanyuPinyinCaseType.LOWERCASE
                toneType = HanyuPinyinToneType.WITHOUT_TONE
            }

            val sb = java.lang.StringBuilder()

            for (c in text) {
                if (c.toString().matches(Regex("[\\u4E00-\\u9FA5]"))) {
                    val pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, format)
                    if (pinyinArray != null && pinyinArray.isNotEmpty()) {
                        sb.append(pinyinArray[0]).append(" ")
                    } else {
                        sb.append(c)
                    }
                } else {
                    sb.append(c)
                }
            }

            sb.toString().replace(Regex("\\s+"), " ").trim()
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    fun romanizeKorean(text: String): String {
        val romajaBuilder = java.lang.StringBuilder()
        var prevFinal: String? = null
        for (i in text.indices) {
            val char = text[i]
            if (char in '\uAC00'..'\uD7A3') {
                val syllableIndex = char.code - 0xAC00
                val choIndex = syllableIndex / (21 * 28)
                val jungIndex = (syllableIndex % (21 * 28)) / 28
                val jongIndex = syllableIndex % 28

                val choChar = (0x1100 + choIndex).toChar().toString()
                val jungChar = (0x1161 + jungIndex).toChar().toString()
                val jongChar = if (jongIndex == 0) null else (0x11A7 + jongIndex).toChar().toString()

                if (prevFinal != null) {
                    val contextKey = prevFinal + choChar
                    val jong = HANGUL_ROMAJA_MAP["jong"]?.get(contextKey) ?: HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal) ?: prevFinal
                    romajaBuilder.append(jong)
                }

                val cho = HANGUL_ROMAJA_MAP["cho"]?.get(choChar) ?: choChar
                val jung = HANGUL_ROMAJA_MAP["jung"]?.get(jungChar) ?: jungChar
                romajaBuilder.append(cho).append(jung)
                prevFinal = jongChar
            } else {
                if (prevFinal != null) {
                    romajaBuilder.append(HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal) ?: prevFinal)
                    prevFinal = null
                }
                romajaBuilder.append(char)
            }
        }
        if (prevFinal != null) romajaBuilder.append(HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal) ?: prevFinal)
        return romajaBuilder.toString()
    }

    fun romanizeHindi(text: String): String {
        val sb = java.lang.StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            var consumed = false
            if (i + 1 < text.length) {
                val twoCharCandidate = text.substring(i, i + 2)
                val mappedTwoChar = DEVANAGARI_ROMAJI_MAP[twoCharCandidate]
                if (mappedTwoChar != null) {
                    sb.append(mappedTwoChar)
                    i += 2
                    consumed = true
                }
            }
            if (!consumed) {
                val charStr = text[i].toString()
                sb.append(DEVANAGARI_ROMAJI_MAP[charStr] ?: charStr)
                i += 1
            }
        }
        return sb.toString()
    }

    fun romanizePunjabi(text: String): String {
        val sb = java.lang.StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val char = text[i]
            var consumed = false
            if (char == '\u0A71') {
                if (i + 1 < text.length) {
                    val nextCharStr = text[i+1].toString()
                    val nextMapped = GURMUKHI_ROMAJI_MAP[nextCharStr]
                    if (nextMapped != null && nextMapped.isNotEmpty()) {
                        sb.append(nextMapped[0])
                    }
                }
                i++
                continue
            }
            if (i + 1 < text.length) {
                val twoCharCandidate = text.substring(i, i + 2)
                val mappedTwoChar = GURMUKHI_ROMAJI_MAP[twoCharCandidate]
                if (mappedTwoChar != null) {
                    sb.append(mappedTwoChar)
                    i += 2
                    consumed = true
                }
            }
            if (!consumed) {
                val str = char.toString()
                sb.append(GURMUKHI_ROMAJI_MAP[str] ?: str)
                i++
            }
        }
        return sb.toString()
    }

    fun romanizeCyrillic(text: String): String? {
        if (text.isEmpty()) return null
        val cyrillicChars = text.filter { it in '\u0400'..'\u04FF' }
        if (cyrillicChars.isEmpty() || (cyrillicChars.length == 1 && (cyrillicChars[0] == 'е' || cyrillicChars[0] == 'Е'))) return null

        return when {
            isRussian(text) -> processCyrillicWordByWord(text, RUSSIAN_ROMAJI_MAP, isRussian = true)
            isUkrainian(text) -> processUkrainian(text)
            isSerbian(text) -> processCyrillicWordByWord(text, SERBIAN_ROMAJI_MAP)
            isBulgarian(text) -> processCyrillicWordByWord(text, BULGARIAN_ROMAJI_MAP)
            isBelarusian(text) -> processBelarusian(text)
            isKyrgyz(text) -> processCyrillicWordByWord(text, KYRGYZ_ROMAJI_MAP)
            isMacedonian(text) -> processCyrillicWordByWord(text, MACEDONIAN_ROMAJI_MAP)
            else -> processCyrillicWordByWord(text, emptyMap())
        }
    }

    private fun processCyrillicWordByWord(text: String, specificMap: Map<String, String>, isRussian: Boolean = false): String {
        val romajiBuilder = java.lang.StringBuilder(text.length)
        val words = text.split("((?<=\\s|[.,!?;])|(?=\\s|[.,!?;]))".toRegex()).filter { it.isNotEmpty() }

        words.forEachIndexed { _, word ->
            if (word.matches("[.,!?;]".toRegex()) || word.isBlank()) {
                romajiBuilder.append(word)
            } else {
                var charIndex = 0
                while (charIndex < word.length) {
                    var consumed = false
                    if (isRussian && charIndex + 2 < word.length) {
                        val threeChar = word.substring(charIndex, charIndex + 3)
                        if (specificMap.containsKey(threeChar)) {
                            romajiBuilder.append(specificMap[threeChar])
                            charIndex += 3
                            consumed = true
                        }
                    }
                    if (!consumed) {
                        val charStr = word[charIndex].toString()
                        if (isRussian && (charStr == "е" || charStr == "Е") && (charIndex == 0 || word[charIndex - 1].isWhitespace())) {
                            romajiBuilder.append(if (charStr == "е") "ye" else "Ye")
                        } else {
                            romajiBuilder.append(specificMap[charStr] ?: GENERAL_CYRILLIC_ROMAJI_MAP[charStr] ?: charStr)
                        }
                        charIndex += 1
                    }
                }
            }
        }
        return romajiBuilder.toString()
    }

    private fun processUkrainian(text: String): String {
        val romajiBuilder = java.lang.StringBuilder(text.length)
        val words = text.split("((?<=\\s|[.,!?;])|(?=\\s|[.,!?;]))".toRegex()).filter { it.isNotEmpty() }

        words.forEach { word ->
            if (word.matches("[.,!?;]".toRegex()) || word.isBlank()) {
                romajiBuilder.append(word)
            } else {
                var charIndex = 0
                while (charIndex < word.length) {
                    val charStr = word[charIndex].toString()
                    var processed = false
                    if (charIndex > 0 && word[charIndex - 1].isLetter() && !"АаЕеЄєИиІіЇїОоУуЮюЯяЫыЭэ".contains(word[charIndex - 1])) {
                        if (charStr == "Ю") { romajiBuilder.append("Iu"); processed = true }
                        else if (charStr == "ю") { romajiBuilder.append("iu"); processed = true }
                        else if (charStr == "Я") { romajiBuilder.append("Ia"); processed = true }
                        else if (charStr == "я") { romajiBuilder.append("ia"); processed = true }
                    }
                    if (!processed) {
                        romajiBuilder.append(UKRAINIAN_ROMAJI_MAP[charStr] ?: GENERAL_CYRILLIC_ROMAJI_MAP[charStr] ?: charStr)
                    }
                    charIndex++
                }
            }
        }
        return romajiBuilder.toString()
    }

    private fun processBelarusian(text: String): String {
        val romajiBuilder = java.lang.StringBuilder(text.length)
        val words = text.split("((?<=\\s|[.,!?;])|(?=\\s|[.,!?;]))".toRegex()).filter { it.isNotEmpty() }

        words.forEach { word ->
            if (word.matches("[.,!?;]".toRegex()) || word.isBlank()) {
                romajiBuilder.append(word)
            } else {
                var charIndex = 0
                while (charIndex < word.length) {
                    val charStr = word[charIndex].toString()
                    if ((charStr == "е" || charStr == "Е") && (charIndex == 0 || word[charIndex - 1].isWhitespace())) {
                        romajiBuilder.append(if (charStr == "е") "ye" else "Ye")
                    } else {
                        romajiBuilder.append(BELARUSIAN_ROMAJI_MAP[charStr] ?: GENERAL_CYRILLIC_ROMAJI_MAP[charStr] ?: charStr)
                    }
                    charIndex += 1
                }
            }
        }
        return romajiBuilder.toString()
    }
}
