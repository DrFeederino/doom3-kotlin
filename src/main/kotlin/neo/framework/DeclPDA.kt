package neo.framework

import neo.framework.DeclManager.declType_t
import neo.framework.DeclManager.idDecl
import neo.idlib.Text.Lexer
import neo.idlib.Text.Lexer.idLexer
import neo.idlib.Text.Str.idStr
import neo.idlib.Text.Token.idToken
import neo.idlib.containers.idStrList
import neo.idlib.idException

class DeclPDA {
    /*
     ===============================================================================

     idDeclPDA

     ===============================================================================
     */
    class idDeclEmail  //
    //
        : idDecl() {
        private lateinit var date: idStr
        private lateinit var from: idStr
        private lateinit var image: idStr
        private lateinit var subject: idStr
        private lateinit var text: idStr
        private lateinit var to: idStr
        override fun DefaultDefinition(): String {
            return """{
                                {
                                    to	5Mail recipient
                                    subject	5Nothing
                                    from	5No one
                                }
                            }"""
        }

        @Throws(idException::class)
        override fun Parse(_text: String, textLength: Int): Boolean {
            val src = idLexer()
            src.LoadMemory(_text, textLength, GetFileName(), GetLineNum())
            src.SetFlags(Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_ALLOWPATHNAMES or Lexer.LEXFL_ALLOWMULTICHARLITERALS or Lexer.LEXFL_ALLOWBACKSLASHSTRINGCONCAT or Lexer.LEXFL_NOFATALERRORS)
            src.SkipUntilString("{")
            text = idStr("")
            // scan through, identifying each individual parameter
            while (true) {
                val token = idToken()
                if (!src.ReadToken(token)) {
                    break
                }
                if (token.toString() == "}") {
                    break
                }
                if (0 == token.Icmp("subject")) {
                    src.ReadToken(token)
                    subject = token
                    continue
                }
                if (0 == token.Icmp("to")) {
                    src.ReadToken(token)
                    to = token
                    continue
                }
                if (0 == token.Icmp("from")) {
                    src.ReadToken(token)
                    from = token
                    continue
                }
                if (0 == token.Icmp("date")) {
                    src.ReadToken(token)
                    date = token
                    continue
                }
                if (0 == token.Icmp("text")) {
                    src.ReadToken(token)
                    if (token.toString() != "{") {
                        src.Warning("Email decl '%s' had a parse error", GetName())
                        return false
                    }
                    while (src.ReadToken(token) && token.toString() != "}") {
                        text.Append(token)
                    }
                    continue
                }
                if (0 == token.Icmp("image")) {
                    src.ReadToken(token)
                    image = token
                    continue
                }
            }
            if (src.HadError()) {
                src.Warning("Email decl '%s' had a parse error", GetName())
                return false
            }
            return true
        }

        override fun FreeData() {}

        @Throws(idException::class)
        override fun Print() {
            Common.common.Printf("Implement me\n")
        }

        @Throws(idException::class)
        override fun List() {
            Common.common.Printf("Implement me\n")
        }

        //
        fun GetFrom(): String {
            return from.toString()
        }

        fun GetBody(): String {
            return text.toString()
        }

        fun GetSubject(): String {
            return subject.toString()
        }

        fun GetDate(): String {
            return date.toString()
        }

        fun GetTo(): String {
            return to.toString()
        }

        fun GetImage(): String {
            return image.toString()
        }
    }

    class idDeclVideo  //
    //
        : idDecl() {
        private lateinit var audio //TODO:construction!?
                : idStr
        private lateinit var info: idStr
        private lateinit var preview: idStr
        private lateinit var video: idStr
        private lateinit var videoName: idStr
        override fun DefaultDefinition(): String {
            return """{
	{
		name	5Default Video
	}
}"""
        }

        @Throws(idException::class)
        override fun Parse(_text: String, textLength: Int): Boolean {
            val src = idLexer()
            src.LoadMemory(_text, textLength, GetFileName(), GetLineNum())
            src.SetFlags(Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_ALLOWPATHNAMES or Lexer.LEXFL_ALLOWMULTICHARLITERALS or Lexer.LEXFL_ALLOWBACKSLASHSTRINGCONCAT or Lexer.LEXFL_NOFATALERRORS)
            src.SkipUntilString("{")

            // scan through, identifying each individual parameter
            while (true) {
                val token = idToken()
                if (!src.ReadToken(token)) {
                    break
                }
                if (token.toString() == "}") {
                    break
                }
                if (0 == token.Icmp("name")) {
                    src.ReadToken(token)
                    videoName = token
                    continue
                }
                if (0 == token.Icmp("preview")) {
                    src.ReadToken(token)
                    preview = token
                    continue
                }
                if (0 == token.Icmp("video")) {
                    src.ReadToken(token)
                    video = token
                    DeclManager.declManager.FindMaterial(video)
                    continue
                }
                if (0 == token.Icmp("info")) {
                    src.ReadToken(token)
                    info = token
                    continue
                }
                if (0 == token.Icmp("audio")) {
                    src.ReadToken(token)
                    audio = token
                    DeclManager.declManager.FindSound(audio)
                    continue
                }
            }
            if (src.HadError()) {
                src.Warning("Video decl '%s' had a parse error", GetName())
                return false
            }
            return true
        }

        override fun FreeData() {}

        @Throws(idException::class)
        override fun Print() {
            Common.common.Printf("Implement me\n")
        }

        @Throws(idException::class)
        override fun List() {
            Common.common.Printf("Implement me\n")
        }

        fun GetRoq(): String {
            return video.toString()
        }

        fun GetWave(): String {
            return audio.toString()
        }

        fun GetVideoName(): String {
            return videoName.toString()
        }

        fun GetInfo(): String {
            return info.toString()
        }

        fun GetPreview(): String {
            return preview.toString()
        }
    }

    class idDeclAudio  //
    //
        : idDecl() {
        private lateinit var audio: idStr
        private lateinit var audioName: idStr
        private lateinit var info: idStr
        private lateinit var preview //TODO:construction!
                : idStr

        override fun DefaultDefinition(): String {
            return """{
	{
		name	5Default Audio
	}
}"""
        }

        @Throws(idException::class)
        override fun Parse(text: String, textLength: Int): Boolean {
            val src = idLexer()
            src.LoadMemory(text, textLength, GetFileName(), GetLineNum())
            src.SetFlags(Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_ALLOWPATHNAMES or Lexer.LEXFL_ALLOWMULTICHARLITERALS or Lexer.LEXFL_ALLOWBACKSLASHSTRINGCONCAT or Lexer.LEXFL_NOFATALERRORS)
            src.SkipUntilString("{")

            // scan through, identifying each individual parameter
            while (true) {
                val token = idToken()
                if (!src.ReadToken(token)) {
                    break
                }
                if (token.toString() == "}") {
                    break
                }
                if (0 == token.Icmp("name")) {
                    src.ReadToken(token)
                    audioName = token
                    continue
                }
                if (0 == token.Icmp("audio")) {
                    src.ReadToken(token)
                    audio = token
                    DeclManager.declManager.FindSound(audio)
                    continue
                }
                if (0 == token.Icmp("info")) {
                    src.ReadToken(token)
                    info = token
                    continue
                }
                if (0 == token.Icmp("preview")) {
                    src.ReadToken(token)
                    preview = token
                    continue
                }
            }
            if (src.HadError()) {
                src.Warning("Audio decl '%s' had a parse error", GetName())
                return false
            }
            return true
        }

        override fun FreeData() {}

        @Throws(idException::class)
        override fun Print() {
            Common.common.Printf("Implement me\n")
        }

        @Throws(idException::class)
        override fun List() {
            Common.common.Printf("Implement me\n")
        }

        fun GetAudioName(): String {
            return audioName.toString()
        }

        fun GetWave(): String {
            return audio.toString()
        }

        fun GetInfo(): String {
            return info.toString()
        }

        fun GetPreview(): String {
            return preview.toString()
        }
    }

    class idDeclPDA : idDecl() {
        private val audios: idStrList
        private val emails: idStrList
        private val fullName: idStr
        private val icon: idStr
        private val id: idStr
        private var originalEmails: Int
        private var originalVideos: Int
        private val pdaName: idStr
        private val post: idStr
        private val security: idStr
        private val title: idStr
        private val videos: idStrList
        override fun DefaultDefinition(): String {
            return """{
	                    name  "default pda"
                    }"""
        }

        @Throws(idException::class)
        override fun Parse(text: String, textLength: Int): Boolean {
            val src = idLexer()
            val token = idToken()
            src.LoadMemory(text, textLength, GetFileName(), GetLineNum())
            src.SetFlags(DeclManager.DECL_LEXER_FLAGS)
            src.SkipUntilString("{")

            // scan through, identifying each individual parameter
            while (true) {
                if (!src.ReadToken(token)) {
                    break
                }
                if (token.toString() == "}") {
                    break
                }
                if (0 == token.Icmp("name")) {
                    src.ReadToken(token)
                    pdaName.set(token)
                    continue
                }
                if (0 == token.Icmp("fullname")) {
                    src.ReadToken(token)
                    fullName.set(token)
                    continue
                }
                if (0 == token.Icmp("icon")) {
                    src.ReadToken(token)
                    icon.set(token)
                    continue
                }
                if (0 == token.Icmp("id")) {
                    src.ReadToken(token)
                    id.set(token)
                    continue
                }
                if (0 == token.Icmp("post")) {
                    src.ReadToken(token)
                    post.set(token)
                    continue
                }
                if (0 == token.Icmp("title")) {
                    src.ReadToken(token)
                    title.set(token)
                    continue
                }
                if (0 == token.Icmp("security")) {
                    src.ReadToken(token)
                    security.set(token)
                    continue
                }
                if (0 == token.Icmp("pda_email")) {
                    src.ReadToken(token)
                    emails.add(token.toString())
                    DeclManager.declManager.FindType(declType_t.DECL_EMAIL, token)
                    continue
                }
                if (0 == token.Icmp("pda_audio")) {
                    src.ReadToken(token)
                    audios.add(token.toString())
                    DeclManager.declManager.FindType(declType_t.DECL_AUDIO, token)
                    continue
                }
                if (0 == token.Icmp("pda_video")) {
                    src.ReadToken(token)
                    videos.add(token.toString())
                    DeclManager.declManager.FindType(declType_t.DECL_VIDEO, token)
                    continue
                }
            }
            if (src.HadError()) {
                src.Warning("PDA decl '%s' had a parse error", GetName())
                return false
            }
            originalVideos = videos.size()
            originalEmails = emails.size()
            return true
        }

        override fun FreeData() {
            videos.clear()
            audios.clear()
            emails.clear()
            originalEmails = 0
            originalVideos = 0
        }

        @Throws(idException::class)
        override fun Print() {
            Common.common.Printf("Implement me\n")
        }

        @Throws(idException::class)
        override fun List() {
            Common.common.Printf("Implement me\n")
        }

        @Throws(idException::class)
        fun AddVideo(_name: String, unique: Boolean /*= true*/) {
            val name = idStr(_name)
            if (unique && videos.Find(name) != null) {
                return
            }
            if (DeclManager.declManager.FindType(declType_t.DECL_VIDEO, _name, false) == null) {
                Common.common.Printf("Video %s not found\n", name)
                return
            }
            videos.add(name)
        }

        @Throws(idException::class)
        fun AddAudio(_name: String, unique: Boolean /*= true*/) {
            val name = idStr(_name)
            if (unique && audios.Find(name) != null) {
                return
            }
            if (DeclManager.declManager.FindType(declType_t.DECL_AUDIO, _name, false) == null) {
                Common.common.Printf("Audio log %s not found\n", name)
                return
            }
            audios.add(name)
        }


        @Throws(idException::class)
        fun AddEmail(_name: String, unique: Boolean = true /*= true*/) {
            val name = idStr(_name)
            if (unique && emails.Find(name) != null) {
                return
            }
            if (DeclManager.declManager.FindType(declType_t.DECL_EMAIL, _name, false) == null) {
                Common.common.Printf("Email %s not found\n", name)
                return
            }
            emails.add(name)
        }

        fun RemoveAddedEmailsAndVideos() {
            var num = emails.size()
            if (originalEmails < num) {
                while (num != 0 && num > originalEmails) {
                    emails.removeAtIndex(--num)
                }
            }
            num = videos.size()
            if (originalVideos < num) {
                while (num != 0 && num > originalVideos) {
                    videos.removeAtIndex(--num)
                }
            }
        }

        fun GetNumVideos(): Int {
            return videos.size()
        }

        fun GetNumAudios(): Int {
            return audios.size()
        }

        fun GetNumEmails(): Int {
            return emails.size()
        }

        @Throws(idException::class)
        fun GetVideoByIndex(index: Int): idDeclVideo? {
            return if (index >= 0 && index < videos.size()) {
                DeclManager.declManager.FindType(declType_t.DECL_VIDEO, videos.get(index), false) as idDeclVideo
            } else null
        }

        @Throws(idException::class)
        fun GetAudioByIndex(index: Int): idDeclAudio? {
            return if (index >= 0 && index < audios.size()) {
                DeclManager.declManager.FindType(declType_t.DECL_AUDIO, audios.get(index), false) as idDeclAudio
            } else null
        }

        @Throws(idException::class)
        fun GetEmailByIndex(index: Int): idDeclEmail? {
            return if (index >= 0 && index < emails.size()) {
                DeclManager.declManager.FindType(declType_t.DECL_EMAIL, emails.get(index), false) as idDeclEmail
            } else null
        }

        fun SetSecurity(sec: String) {
            security.set(sec)
        }

        fun GetPdaName(): String {
            return pdaName.toString()
        }

        fun GetSecurity(): String {
            return security.toString()
        }

        fun GetFullName(): String {
            return fullName.toString()
        }

        fun GetIcon(): String {
            return icon.toString()
        }

        fun GetPost(): String {
            return post.toString()
        }

        fun GetID(): String {
            return id.toString()
        }

        fun GetTitle(): String {
            return title.toString()
        }

        //
        //
        init {
            videos = idStrList()
            audios = idStrList()
            emails = idStrList()
            pdaName = idStr()
            fullName = idStr()
            icon = idStr()
            id = idStr()
            post = idStr()
            title = idStr()
            security = idStr()
            originalVideos = 0
            originalEmails = originalVideos
        }
    }
}