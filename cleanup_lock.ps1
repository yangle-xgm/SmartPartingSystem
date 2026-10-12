[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
try {
    $word = [System.Runtime.InteropServices.Marshal]::GetActiveObject('Word.Application')
    Write-Output ("attached to running Word, docs open: " + $word.Documents.Count)
    for ($i = $word.Documents.Count; $i -ge 1; $i--) {
        $d = $word.Documents.Item($i)
        Write-Output ("  doc: " + $d.Name)
        if ($d.Name -like 'userdoc_check*') { $d.Close($false); Write-Output "  -> closed" }
    }
} catch {
    Write-Output ("no running Word COM instance: " + $_.Exception.Message)
}
Start-Sleep -Seconds 1
Remove-Item "d:\JAVA EE\SmartPartingSystem\userdoc_check.docx" -Force -ErrorAction SilentlyContinue
if (Test-Path "d:\JAVA EE\SmartPartingSystem\userdoc_check.docx") { Write-Output "STILL LOCKED" } else { Write-Output "DELETED OK" }