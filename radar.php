<?php
header('Content-Type: text/plain');

$directory = 'C:\\git\\hooku\\radar\\downloaded_images';
$mp4_files = glob($directory . '\\*.mp4');
$webp_files = glob($directory . '\\*.webp');

if ($mp4_files === false) {
    $mp4_files = [];
}

if ($webp_files === false) {
    $webp_files = [];
}

// Get the current month, day, and hour
$current_month_day = date('md');
$current_hour = date('H');

// Filter webp files to include only those in the current month, day, and hour range
$filtered_webp_files = array_filter($webp_files, function($file) use ($current_month_day, $current_hour) {
    $filename = basename($file, '.webp');
    $date_part = substr($filename, 0, 4);
    $time_part = substr($filename, 5, 4);
    $file_hour = substr($time_part, 0, 2);
    return $date_part == $current_month_day && $file_hour == $current_hour;
});

$files = array_merge($mp4_files, $filtered_webp_files);

foreach ($files as $file) {
    echo basename($file) . "\n";
}
?>