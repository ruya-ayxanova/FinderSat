import os
from xml.sax.saxutils import escape
K = ["tab_all","tab_favorites","tab_inactive","search_hint","settings","language","language_system",
"data_updated","data_source","status_active","status_inactive","status_unverified","update_catalog",
"update_done","update_failed","no_results","transponders_none","pos_east","pos_west","filter_all"]
T = {
"": ["All","Favorites","Inactive","Search satellite, operator or position","Settings","Language","System default","Data updated: %1$s","Source: %1$s","Active","Inactive","Unverified","Update catalog","Catalog updated","Update failed; previous data kept","No results","No transponder data","E","W","All"],
"tr": ["Tümü","Favoriler","Aktif olmayanlar","Uydu, operatör veya konum ara","Ayarlar","Dil","Sistem varsayılanı","Veri güncelleme tarihi: %1$s","Kaynak: %1$s","Aktif","Aktif değil","Doğrulanmadı","Kataloğu güncelle","Katalog güncellendi","Güncelleme başarısız; eski veri korundu","Sonuç yok","Transponder verisi yok","D","B","Tümü"],
"az": ["Hamısı","Sevimlilər","Aktiv olmayanlar","Peyk, operator və ya mövqe axtar","Ayarlar","Dil","Sistem defoltu","Məlumatın yenilənmə tarixi: %1$s","Mənbə: %1$s","Aktiv","Aktiv deyil","Təsdiqlənməyib","Kataloqu yenilə","Kataloq yeniləndi","Yenilənmə uğursuz oldu; köhnə məlumat saxlanıldı","Nəticə yoxdur","Transponder məlumatı yoxdur","Ş","Q","Hamısı"],
"b+az+Arab": ["هامیسی","سئویملیلر","آکتیو اولمایانلار","پیک، اوپراتور یا مؤقع آختار","تنظیملر","دیل","سیستم پیش‌فرضی","داده یئنیلنمه تاریخی: %1$s","منبع: %1$s","آکتیو","آکتیو دئیل","تأیید اولونماییب","کاتالوقو یئنیله","کاتالوق یئنیلندی","یئنیلمه اوغورسوز اولدو؛ اسکی داده ساخلانیلدی","نتیجه یوخدور","ترانسپوندر داده‌سی یوخدور","ش","غ","هامیسی"],
"b+azb": ["هامیسی","علاقه‌لیلر","فعال اولمایانلار","ماهواره، اوپراتور یا موقعیت آختار","تنظیملر","دیل","سیستم پیش‌فرضی","داده‌نین یئنیلنمه تاریخی: %1$s","منبع: %1$s","فعال","فعال دئییل","تأیید اولونماییب","کاتالوقو یئنیله","کاتالوق یئنیلندی","یئنیلمه اوغورسوز اولدو؛ کهنه داده ساخلانیلدی","نتیجه یوخدور","ترانسپوندر داده‌سی یوخدور","ش","غ","هامیسی"],
"fa": ["همه","علاقه‌مندی‌ها","غیرفعال‌ها","جستجوی ماهواره، اپراتور یا موقعیت","تنظیمات","زبان","پیش‌فرض سیستم","تاریخ به‌روزرسانی داده‌ها: %1$s","منبع: %1$s","فعال","غیرفعال","تأییدنشده","به‌روزرسانی فهرست","فهرست به‌روزرسانی شد","به‌روزرسانی ناموفق بود؛ داده‌های قبلی حفظ شد","نتیجه‌ای یافت نشد","داده‌ای برای ترانسپوندر وجود ندارد","ش","غ","همه"],
"de": ["Alle","Favoriten","Inaktiv","Satellit, Betreiber oder Position suchen","Einstellungen","Sprache","Systemstandard","Daten aktualisiert am: %1$s","Quelle: %1$s","Aktiv","Inaktiv","Nicht verifiziert","Katalog aktualisieren","Katalog aktualisiert","Aktualisierung fehlgeschlagen; alte Daten bleiben erhalten","Keine Ergebnisse","Keine Transponderdaten","O","W","Alle"],
"ru": ["Все","Избранное","Неактивные","Поиск спутника, оператора или позиции","Настройки","Язык","Как в системе","Данные обновлены: %1$s","Источник: %1$s","Активен","Неактивен","Не подтверждён","Обновить каталог","Каталог обновлён","Не удалось обновить; старые данные сохранены","Ничего не найдено","Нет данных о транспондерах","в. д.","з. д.","Все"],
"ar": ["الكل","المفضلة","غير النشطة","ابحث عن قمر أو مشغّل أو موقع","الإعدادات","اللغة","افتراضي النظام","تاريخ تحديث البيانات: %1$s","المصدر: %1$s","نشط","غير نشط","غير موثّق","تحديث الفهرس","تم تحديث الفهرس","فشل التحديث؛ تم الاحتفاظ بالبيانات السابقة","لا توجد نتائج","لا توجد بيانات للمرسِلات","ش","غ","الكل"],
"fr": ["Tous","Favoris","Inactifs","Rechercher un satellite, un opérateur ou une position","Paramètres","Langue","Par défaut du système","Données mises à jour le : %1$s","Source : %1$s","Actif","Inactif","Non vérifié","Mettre à jour le catalogue","Catalogue mis à jour","Échec de la mise à jour ; anciennes données conservées","Aucun résultat","Aucune donnée de transpondeur","E","O","Tous"],
"es": ["Todos","Favoritos","Inactivos","Buscar satélite, operador o posición","Ajustes","Idioma","Predeterminado del sistema","Datos actualizados: %1$s","Fuente: %1$s","Activo","Inactivo","Sin verificar","Actualizar catálogo","Catálogo actualizado","Error al actualizar; se conservaron los datos anteriores","Sin resultados","Sin datos de transpondedores","E","O","Todos"],
"it": ["Tutti","Preferiti","Inattivi","Cerca satellite, operatore o posizione","Impostazioni","Lingua","Predefinito di sistema","Dati aggiornati il: %1$s","Fonte: %1$s","Attivo","Inattivo","Non verificato","Aggiorna catalogo","Catalogo aggiornato","Aggiornamento non riuscito; dati precedenti conservati","Nessun risultato","Nessun dato sui transponder","E","O","Tutti"],
"pt": ["Todos","Favoritos","Inativos","Pesquisar satélite, operadora ou posição","Configurações","Idioma","Padrão do sistema","Dados atualizados em: %1$s","Fonte: %1$s","Ativo","Inativo","Não verificado","Atualizar catálogo","Catálogo atualizado","Falha na atualização; dados anteriores mantidos","Nenhum resultado","Sem dados de transponders","L","O","Todos"],
"zh": ["全部","收藏","已停用","搜索卫星、运营商或轨位","设置","语言","跟随系统","数据更新时间：%1$s","来源：%1$s","在用","已停用","未核实","更新目录","目录已更新","更新失败，已保留原有数据","无结果","无转发器数据","东","西","全部"],
"ja": ["すべて","お気に入り","非稼働","衛星、事業者、軌道位置を検索","設定","言語","システムのデフォルト","データ更新日：%1$s","情報源：%1$s","稼働中","非稼働","未確認","カタログを更新","カタログを更新しました","更新に失敗しました。以前のデータを保持しました","結果なし","トランスポンダー情報なし","東","西","すべて"],
"ko": ["전체","즐겨찾기","비활성","위성, 사업자 또는 위치 검색","설정","언어","시스템 기본값","데이터 업데이트: %1$s","출처: %1$s","활성","비활성","미확인","카탈로그 업데이트","카탈로그가 업데이트되었습니다","업데이트 실패, 이전 데이터를 유지했습니다","결과 없음","트랜스폰더 정보 없음","동","서","전체"],
"hi": ["सभी","पसंदीदा","निष्क्रिय","उपग्रह, ऑपरेटर या स्थान खोजें","सेटिंग्स","भाषा","सिस्टम डिफ़ॉल्ट","डेटा अपडेट की तारीख: %1$s","स्रोत: %1$s","सक्रिय","निष्क्रिय","असत्यापित","कैटलॉग अपडेट करें","कैटलॉग अपडेट हो गया","अपडेट विफल रहा; पुराना डेटा सुरक्षित रखा गया","कोई परिणाम नहीं","ट्रांसपोंडर डेटा उपलब्ध नहीं","पू","प","सभी"],
"ur": ["سب","پسندیدہ","غیر فعال","سیٹلائٹ، آپریٹر یا مقام تلاش کریں","ترتیبات","زبان","سسٹم کی ڈیفالٹ","ڈیٹا اپ ڈیٹ کی تاریخ: %1$s","ماخذ: %1$s","فعال","غیر فعال","غیر تصدیق شدہ","کیٹلاگ اپ ڈیٹ کریں","کیٹلاگ اپ ڈیٹ ہو گیا","اپ ڈیٹ ناکام رہا؛ پرانا ڈیٹا محفوظ رہا","کوئی نتیجہ نہیں","ٹرانسپونڈر کا ڈیٹا دستیاب نہیں","E","W","سب"],
"b+ckb": ["هەموو","دڵخوازەکان","ناچالاکەکان","گەڕان بەدوای مانگی دەستکرد، ئۆپەراتۆر یان شوێن","ڕێکخستنەکان","زمان","بنەڕەتی سیستەم","بەرواری نوێکردنەوەی داتا: %1$s","سەرچاوە: %1$s","چالاک","ناچالاک","پشتڕاستنەکراو","نوێکردنەوەی کاتالۆگ","کاتالۆگ نوێکرایەوە","نوێکردنەوە سەرکەوتوو نەبوو؛ داتای کۆن پارێزرا","ئەنجام نییە","داتای ترانسپۆندەر نییە","E","W","هەموو"],
"b+kmr": ["Hemû","Bijare","Ne çalak","Li peyk, operator an cih bigere","Mîheng","Ziman","Bi default a pergalê","Dîroka nûvekirina daneyê: %1$s","Çavkanî: %1$s","Çalak","Ne çalak","Nehatiye piştrastkirin","Katalogê nû bike","Katalog hate nûkirin","Nûvekirin têk çû; daneyên berê hatin parastin","Encam tune","Daneya transponderê tune","E","W","Hemû"],
}
base = "app/src/main/res"
for q, vals in T.items():
    assert len(vals) == len(K), (q, len(vals))
    d = f"{base}/values-{q}" if q else f"{base}/values"
    os.makedirs(d, exist_ok=True)
    lines = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
    if not q:
        lines.append('    <string name="app_name" translatable="false">Pro SatFinder</string>')
    for k, v in zip(K, vals):
        v = escape(v).replace("'", "\\'")
        lines.append(f'    <string name="{k}">{v}</string>')
    lines.append('</resources>')
    open(f"{d}/strings.xml", "w", encoding="utf-8").write("\n".join(lines) + "\n")
print(len(T), "dosya yazıldı")
