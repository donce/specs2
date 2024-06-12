package org.specs2
package io

import control.*
import fp.syntax.*
import data.LruCache
import data.ProcessedStatus.*
import java.io.*
import java.util.regex.Pattern.*
import java.util.regex.Matcher.*
import java.net.URL
import java.util.zip.*
import java.util.regex.Pattern.compile
import java.util.regex.Matcher.quoteReplacement

/** Interface for the FileSystem where effects are denoted with the "Operation" type
  */
case class FileSystem(logger: Logger) extends FilePathReader:

  /** delete a file */
  def deleteFile(filePath: FilePath): Operation[Boolean] =
    Operation.delayed(filePath.toFile.delete)

  /** modify the content of a file */
  def updateFileContent(filePath: FilePath)(update: String => String): Operation[Unit] =
    readFile(filePath).flatMap(s => writeFile(filePath, update(s)))

  /** replace a string in a file */
  def replaceInFile(filePath: FilePath, source: String, target: String): Operation[Unit] =
    updateFileContent(filePath)(_.replace(source, target))

  /** write a string to a file as UTF-8 */
  def writeFile(filePath: FilePath, content: String): Operation[Unit] =
    mkdirs(filePath) >>
      Operation
        .protect {
          val writer = new PrintWriter(filePath.path, "UTF-8")
          try Right(writer.write(content))
          catch { case e: Exception => Left(e) }
          finally writer.close
        }
        .flatMap {
          case Left(e) =>
            logger.exception(e) >>
              logger.warn("could not write file " + filePath.path)

          case Right(_) =>
            Operation.unit
        }

  /** execute an operation with a File, then delete it */
  def withEphemeralFile(path: FilePath)(operation: Operation[Unit]): Operation[Unit] =
    operation.thenFinally(deleteFile(path).void)

  /** create a directory and its parent directories */
  def mkdirs(path: DirectoryPath): Operation[Unit] =
    Operation.protect(path.toFile.mkdirs).void

  /** create a the directory containing a file and its parent directories */
  def mkdirs(path: FilePath): Operation[Unit] =
    mkdirs(path.dir)

  /** Unjaring the same thing over and over is inefficient. LRU cache to keep track of what was already done. */
  private val UnjarLRUCache = new LruCache[(URL, DirectoryPath, String)](maxSize = 1000)

  /** Unjar the jar (or zip file) specified by "path" to the "dest" directory. Filters files which shouldn't be
    * extracted with a regular expression. This is only done once per argument list (unless eventually evicted from LRU
    * cache).
    * @param jarUrl
    *   path of the jar file
    * @param dest
    *   destination directory path
    * @param regexFilter
    *   regular expression filtering files which shouldn't be extracted; the expression must capture the path of an
    *   entry as group 1 which will then be used relative to dirPath as target path for that entry
    * @see
    *   [[unjar]]
    */
  def unjarOnce(jarUrl: URL, dest: DirectoryPath, regexFilter: String): Operation[Unit] =
    for
      status <- UnjarLRUCache.register((jarUrl, dest, regexFilter))
      _ <- unjar(jarUrl, dest, regexFilter).when(status == ToProcess)
    yield ()

  /** Unjar the jar (or zip file) specified by "path" to the "dest" directory. Filters files which shouldn't be
    * extracted with a regular expression.
    * @param jarUrl
    *   path of the jar file
    * @param dest
    *   destination directory path
    * @param regexFilter
    *   regular expression filtering files which shouldn't be extracted; the expression must capture the path of an
    *   entry as group 1 which will then be used relative to dirPath as target path for that entry
    *
    * @see
    *   [[unjarOnce]]
    */
  def unjar(jarUrl: URL, dest: DirectoryPath, regexFilter: String): Operation[Unit] =
    val regex = compile(regexFilter)
    val uis = jarUrl.openStream()
    val zis = new ZipInputStream(new BufferedInputStream(uis))

    @annotation.tailrec
    def extractEntry(entry: ZipEntry): Unit =
      if entry != null then
        val matcher = regex.matcher(entry.getName)
        if matcher.matches then
          val target = matcher.replaceFirst(s"${quoteReplacement(dest.path)}$$1")
          if !entry.isDirectory then
            new File(target).getParentFile.mkdirs
            new File(target).createNewFile
            val fos = new FileOutputStream(target)
            val dest = new BufferedOutputStream(fos, 2048)
            try
              copy(zis, dest)
              dest.flush
            finally dest.close

        extractEntry(zis.getNextEntry)

    Operation.delayed {
      try extractEntry(zis.getNextEntry)
      finally zis.close
    }

  /** Copy an input stream to an output stream.
    * @param input
    *   input stream
    * @param output
    *   output stream
    */
  private def copy(input: InputStream, output: OutputStream): Unit =
    val data = new Array[Byte](2048)
    def readData(count: Int): Unit =
      if count != -1 then
        output.write(data, 0, count)
        output.flush
        readData(input.read(data, 0, 2048))
    readData(input.read(data, 0, 2048))

  /** copy the content of a directory to another.
    * @param src
    *   path of the directory to copy
    * @param dest
    *   destination directory path
    */
  def copyDir(src: DirectoryPath, dest: DirectoryPath): Operation[Unit] =
    mkdirs(dest) >>
      listDirectFilePaths(src).flatMap { files =>
        files.toList.map(copyFile(dest)).sequence.void
      } >>
      listDirectDirectoryPaths(src).flatMap { (directories: IndexedSeq[DirectoryPath]) =>
        directories.toList.map(dir => copyDir(dir, dest / dir.name)).sequence.void
      }

  /** copy a file to a destination directory
    * @param filePath
    *   path of the file to copy
    * @param dest
    *   destination directory path
    */
  def copyFile(dest: DirectoryPath)(filePath: FilePath): Operation[Unit] =
    mkdirs(dest) >>
      Operation.delayed {
        copyLock.synchronized {
          import java.nio.file.*
          Files.copy(
            Paths.get(filePath.path),
            Paths.get(dest.path).resolve(Paths.get(filePath.name.name)),
            StandardCopyOption.REPLACE_EXISTING
          )
        }
      }.void

  /** the Files.copy operation is being called concurrently, sometimes to copy the same files when running the Html
    * printer for example. Without a lock a FileAlreadyException can be thrown
    */
  private object copyLock

  /** create a new file */
  def createFile(filePath: FilePath): Operation[Boolean] =
    mkdirs(filePath.dir) >>
      Operation.delayed(filePath.toFile.createNewFile)

  /** delete files or directories */
  def delete(file: FilePath): Operation[Unit] =
    Operation.delayed(file.toFile.delete).void

  /** delete a directory */
  def delete(dir: DirectoryPath): Operation[Unit] =
    for {
      files <- listFilePaths(dir)
      _ <- files.traverse_(delete)
      _ <- delete(dir.toFilePath) // delete the directory once it is empty
    } yield ()
