package app.qingjian.android;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** 按应用版本保留静态数据；学习数据位于独立目录，升级不覆盖。 */
final class ResourceInstaller {
    private ResourceInstaller() { }

    static File install(Context context) throws IOException {
        File directory = new File(context.getFilesDir(), "resources-" + BuildConfig.VERSION_CODE);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建词库目录");
        for (String name : new String[]{"dict.tsv", "english.tsv", "glossary-en.tsv"}) {
            File destination = new File(directory, name);
            if (destination.isFile() && destination.length() > 0) continue;
            File temporary = new File(directory, name + ".tmp");
            try (InputStream input = context.getAssets().open("qingjian/" + name);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[32768];
                int length;
                while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
                output.getFD().sync();
            }
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return directory;
    }
}
