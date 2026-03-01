/*
 * ===========================================================================
 *
 * Doom 3 GPL Source Code
 * Copyright (C) 1999-2011 id Software LLC, a ZeniMax Media company.
 * Translated to Kotlin by Dr. Feederino with support of Claude Code
 *
 * This file is part of the Doom 3 GPL Source Code ("Doom 3 Source Code").
 *
 * Doom 3 Source Code is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Doom 3 Source Code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Doom 3 Source Code. If not, see <http://www.gnu.org/licenses/>.
 *
 * In addition, the Doom 3 Source Code is also subject to certain additional terms.
 * You should have received a copy of these additional terms immediately following
 * the terms and conditions of the GNU General Public License which accompanied
 * the Doom 3 Source Code. If not, please request a copy in writing from
 * id Software at the address below.
 *
 * If you have questions concerning this license or the applicable additional terms,
 * you may contact in writing id Software LLC, c/o ZeniMax Media Inc., Suite 120,
 * Rockville, Maryland 20850 USA.
 *
 * ===========================================================================
 *
 * Original source: neo/framework/DeclPDA.h, neo/framework/DeclPDA.cpp
 */
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

        idDeclEmail

     ===============================================================================
     */
    class idDeclEmail : idDecl() {
        // FIX: Changed from lateinit to initialized fields — C++ default-constructs
        // idStr to empty. lateinit would crash with UninitializedPropertyAccessException
        // if any field was not set during Parse().
        private val text: idStr = idStr()
        private val subject: idStr = idStr()
        private val date: idStr = idStr()
        private val to: idStr = idStr()
        private val from: idStr = idStr()
        private val image: idStr = idStr()

        /*
         ===================
         idDeclEmail::DefaultDefinition
         ===================
         */
        // FIX: Raw string literal had wrong indentation (source code whitespace leaked
        // into the string). Now matches C++ output exactly.
        override fun DefaultDefinition(): String {
            return "{\n\t{\n\t\tto\t5Mail recipient\n\t\tsubject\t5Nothing\n\t\tfrom\t5No one\n\t}\n}"
        }

        /*
         ================
         idDeclEmail::Parse
         ================
         */
        @Throws(idException::class)
        override fun Parse(_text: String, textLength: Int): Boolean {
            val src = idLexer()
            // FIX: Moved token declaration outside the loop to match C++ (single token
            // reused across iterations). Now uses .set() for value-copy semantics.
            val token = idToken()

            src.LoadMemory(_text, textLength, GetFileName(), GetLineNum())
            src.SetFlags(
                Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_ALLOWPATHNAMES
                        or Lexer.LEXFL_ALLOWMULTICHARLITERALS or Lexer.LEXFL_ALLOWBACKSLASHSTRINGCONCAT
                        or Lexer.LEXFL_NOFATALERRORS
            )
            src.SkipUntilString("{")

            text.set("")
            // scan through, identifying each individual parameter
            while (true) {

                if (!src.ReadToken(token)) {
                    break
                }

                if (token.toString() == "}") {
                    break
                }

                if (0 == token.Icmp("subject")) {
                    src.ReadToken(token)
                    // FIX: Was `subject = token` (reference assignment). Now uses .set()
                    // for value-copy matching C++ `subject = token` (idStr::operator=).
                    subject.set(token)
                    continue
                }

                if (0 == token.Icmp("to")) {
                    src.ReadToken(token)
                    to.set(token)
                    continue
                }

                if (0 == token.Icmp("from")) {
                    src.ReadToken(token)
                    from.set(token)
                    continue
                }

                if (0 == token.Icmp("date")) {
                    src.ReadToken(token)
                    date.set(token)
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
                    image.set(token)
                    continue
                }
            }

            if (src.HadError()) {
                src.Warning("Email decl '%s' had a parse error", GetName())
                return false
            }
            return true
        }

        /*
         ===================
         idDeclEmail::FreeData
         ===================
         */
        override fun FreeData() {}

        /*
         ===============
         idDeclEmail::Print
         ===============
         */
        @Throws(idException::class)
        override fun Print() {
            Common.common.Printf("Implement me\n")
        }

        /*
         ===============
         idDeclEmail::List
         ===============
         */
        @Throws(idException::class)
        override fun List() {
            Common.common.Printf("Implement me\n")
        }

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

    /*
     ===============================================================================

        idDeclVideo

     ===============================================================================
     */
    class idDeclVideo : idDecl() {
        // FIX: Changed from lateinit to initialized fields — matches C++ default
        // construction of idStr members.
        private val preview: idStr = idStr()
        private val video: idStr = idStr()
        private val videoName: idStr = idStr()
        private val info: idStr = idStr()
        private val audio: idStr = idStr()

        /*
         ===================
         idDeclVideo::DefaultDefinition
         ===================
         */
        // FIX: Raw string literal had wrong indentation. Now matches C++ output exactly.
        override fun DefaultDefinition(): String {
            return "{\n\t{\n\t\tname\t5Default Video\n\t}\n}"
        }

        /*
         ================
         idDeclVideo::Parse
         ================
         */
        @Throws(idException::class)
        override fun Parse(text: String, textLength: Int): Boolean {
            val src = idLexer()
            // FIX: Moved token declaration outside the loop to match C++.
            val token = idToken()

            src.LoadMemory(text, textLength, GetFileName(), GetLineNum())
            src.SetFlags(
                Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_ALLOWPATHNAMES
                        or Lexer.LEXFL_ALLOWMULTICHARLITERALS or Lexer.LEXFL_ALLOWBACKSLASHSTRINGCONCAT
                        or Lexer.LEXFL_NOFATALERRORS
            )
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
                    // FIX: Was `videoName = token` (reference assignment). Now uses .set()
                    // for value-copy matching C++ `videoName = token` (idStr::operator=).
                    videoName.set(token)
                    continue
                }

                if (0 == token.Icmp("preview")) {
                    src.ReadToken(token)
                    preview.set(token)
                    continue
                }

                if (0 == token.Icmp("video")) {
                    src.ReadToken(token)
                    video.set(token)
                    DeclManager.declManager.FindMaterial(video)
                    continue
                }

                if (0 == token.Icmp("info")) {
                    src.ReadToken(token)
                    info.set(token)
                    continue
                }

                if (0 == token.Icmp("audio")) {
                    src.ReadToken(token)
                    audio.set(token)
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

        /*
         ===================
         idDeclVideo::FreeData
         ===================
         */
        override fun FreeData() {}

        /*
         ===============
         idDeclVideo::Print
         ===============
         */
        @Throws(idException::class)
        override fun Print() {
            Common.common.Printf("Implement me\n")
        }

        /*
         ===============
         idDeclVideo::List
         ===============
         */
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

    /*
     ===============================================================================

        idDeclAudio

     ===============================================================================
     */
    class idDeclAudio : idDecl() {
        // FIX: Changed from lateinit to initialized fields — matches C++ default
        // construction of idStr members.
        private val audio: idStr = idStr()
        private val audioName: idStr = idStr()
        private val info: idStr = idStr()
        private val preview: idStr = idStr()

        /*
         ===================
         idDeclAudio::DefaultDefinition
         ===================
         */
        // FIX: Raw string literal had wrong indentation. Now matches C++ output exactly.
        override fun DefaultDefinition(): String {
            return "{\n\t{\n\t\tname\t5Default Audio\n\t}\n}"
        }

        /*
         ================
         idDeclAudio::Parse
         ================
         */
        @Throws(idException::class)
        override fun Parse(text: String, textLength: Int): Boolean {
            val src = idLexer()
            // FIX: Moved token declaration outside the loop to match C++.
            val token = idToken()

            src.LoadMemory(text, textLength, GetFileName(), GetLineNum())
            src.SetFlags(
                Lexer.LEXFL_NOSTRINGCONCAT or Lexer.LEXFL_ALLOWPATHNAMES
                        or Lexer.LEXFL_ALLOWMULTICHARLITERALS or Lexer.LEXFL_ALLOWBACKSLASHSTRINGCONCAT
                        or Lexer.LEXFL_NOFATALERRORS
            )
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
                    // FIX: Was `audioName = token` (reference assignment). Now uses .set()
                    // for value-copy matching C++ `audioName = token` (idStr::operator=).
                    audioName.set(token)
                    continue
                }

                if (0 == token.Icmp("audio")) {
                    src.ReadToken(token)
                    audio.set(token)
                    DeclManager.declManager.FindSound(audio)
                    continue
                }

                if (0 == token.Icmp("info")) {
                    src.ReadToken(token)
                    info.set(token)
                    continue
                }

                if (0 == token.Icmp("preview")) {
                    src.ReadToken(token)
                    preview.set(token)
                    continue
                }
            }

            if (src.HadError()) {
                src.Warning("Audio decl '%s' had a parse error", GetName())
                return false
            }
            return true
        }

        /*
         ===================
         idDeclAudio::FreeData
         ===================
         */
        override fun FreeData() {}

        /*
         ===============
         idDeclAudio::Print
         ===============
         */
        @Throws(idException::class)
        override fun Print() {
            Common.common.Printf("Implement me\n")
        }

        /*
         ===============
         idDeclAudio::List
         ===============
         */
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

    /*
     ===============================================================================

        idDeclPDA

     ===============================================================================
     */
    class idDeclPDA : idDecl() {
        private val videos: idStrList = idStrList()
        private val audios: idStrList = idStrList()
        private val emails: idStrList = idStrList()
        private val pdaName: idStr = idStr()
        private val fullName: idStr = idStr()
        private val icon: idStr = idStr()
        private val id: idStr = idStr()
        private val post: idStr = idStr()
        private val title: idStr = idStr()
        private val security: idStr = idStr()
        private var originalEmails: Int = 0
        private var originalVideos: Int = 0

        /*
         ===================
         idDeclPDA::DefaultDefinition
         ===================
         */
        // FIX: Raw string literal had wrong indentation. Now matches C++ output exactly.
        override fun DefaultDefinition(): String {
            return "{\n\tname  \"default pda\"\n}"
        }

        /*
         ================
         idDeclPDA::Parse
         ================
         */
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

        /*
         ===================
         idDeclPDA::FreeData
         ===================
         */
        override fun FreeData() {
            videos.clear()
            audios.clear()
            emails.clear()
            originalEmails = 0
            originalVideos = 0
        }

        /*
         ===============
         idDeclPDA::Print
         ===============
         */
        @Throws(idException::class)
        override fun Print() {
            Common.common.Printf("Implement me\n")
        }

        /*
         ===============
         idDeclPDA::List
         ===============
         */
        @Throws(idException::class)
        override fun List() {
            Common.common.Printf("Implement me\n")
        }

        /*
         =================
         idDeclPDA::AddVideo
         =================
         */
        // FIX: Added default parameter `= true` for unique — was commented out but
        // C++ declares `bool unique = true`.
        @Throws(idException::class)
        fun AddVideo(_name: String, unique: Boolean = true) {
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

        /*
         =================
         idDeclPDA::AddAudio
         =================
         */
        // FIX: Added default parameter `= true` for unique — was commented out but
        // C++ declares `bool unique = true`.
        @Throws(idException::class)
        fun AddAudio(_name: String, unique: Boolean = true) {
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

        /*
         =================
         idDeclPDA::AddEmail
         =================
         */
        @Throws(idException::class)
        fun AddEmail(_name: String, unique: Boolean = true) {
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

        /*
         =================
         idDeclPDA::RemoveAddedEmailsAndVideos
         =================
         */
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

        /*
         =================
         idDeclPDA::SetSecurity
         =================
         */
        fun SetSecurity(sec: String) {
            security.set(sec)
        }

        /*
         =================
         idDeclPDA::GetNumVideos
         =================
         */
        fun GetNumVideos(): Int {
            return videos.size()
        }

        /*
         =================
         idDeclPDA::GetNumAudios
         =================
         */
        fun GetNumAudios(): Int {
            return audios.size()
        }

        /*
         =================
         idDeclPDA::GetNumEmails
         =================
         */
        fun GetNumEmails(): Int {
            return emails.size()
        }

        /*
         =================
         idDeclPDA::GetVideoByIndex
         =================
         */
        @Throws(idException::class)
        fun GetVideoByIndex(index: Int): idDeclVideo? {
            return if (index >= 0 && index < videos.size()) {
                DeclManager.declManager.FindType(declType_t.DECL_VIDEO, videos.get(index), false) as idDeclVideo
            } else null
        }

        /*
         =================
         idDeclPDA::GetAudioByIndex
         =================
         */
        @Throws(idException::class)
        fun GetAudioByIndex(index: Int): idDeclAudio? {
            return if (index >= 0 && index < audios.size()) {
                DeclManager.declManager.FindType(declType_t.DECL_AUDIO, audios.get(index), false) as idDeclAudio
            } else null
        }

        /*
         =================
         idDeclPDA::GetEmailByIndex
         =================
         */
        @Throws(idException::class)
        fun GetEmailByIndex(index: Int): idDeclEmail? {
            return if (index >= 0 && index < emails.size()) {
                DeclManager.declManager.FindType(declType_t.DECL_EMAIL, emails.get(index), false) as idDeclEmail
            } else null
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
    }
}
