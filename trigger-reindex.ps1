$url = "http://localhost:8087/v1/search"

Write-Host "Reindex..."
$r = Invoke-RestMethod "$url/reindex" -Method Post -ContentType "application/json"
Write-Host "$($r.data.status) -- $($r.data.message)"

do {
  Start-Sleep 3
  $s = (Invoke-RestMethod "$url/reindex/status").data
  Write-Host "$($s.status) docs=$($s.documentCount)"
} while ($s.status -ne "COMPLETED" -and $s.status -ne "FAILED")

Write-Host "Done."
