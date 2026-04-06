using System.Text.Json;
using FlowSenseWeb.Models;

namespace FlowSenseWeb.Services;

public class FeedbackService
{
    private readonly string _filePath;
    private readonly SemaphoreSlim _lock = new(1, 1);
    private static readonly JsonSerializerOptions JsonOptions = new() { WriteIndented = true };

    public FeedbackService(IWebHostEnvironment env)
    {
        var dataDir = Path.Combine(env.ContentRootPath, "Data");
        Directory.CreateDirectory(dataDir);
        _filePath = Path.Combine(dataDir, "feedback.json");
    }

    public async Task<List<FeedbackEntry>> GetAllAsync()
    {
        await _lock.WaitAsync();
        try
        {
            if (!File.Exists(_filePath)) return [];
            var json = await File.ReadAllTextAsync(_filePath);
            return JsonSerializer.Deserialize<List<FeedbackEntry>>(json, JsonOptions) ?? [];
        }
        finally { _lock.Release(); }
    }

    public async Task AddAsync(FeedbackEntry entry)
    {
        await _lock.WaitAsync();
        try
        {
            var entries = new List<FeedbackEntry>();
            if (File.Exists(_filePath))
            {
                var json = await File.ReadAllTextAsync(_filePath);
                entries = JsonSerializer.Deserialize<List<FeedbackEntry>>(json, JsonOptions) ?? [];
            }
            entries.Insert(0, entry);
            await File.WriteAllTextAsync(_filePath, JsonSerializer.Serialize(entries, JsonOptions));
        }
        finally { _lock.Release(); }
    }

    public async Task<(double Average, int Count)> GetStatsAsync()
    {
        var entries = await GetAllAsync();
        if (entries.Count == 0) return (0, 0);
        return (entries.Average(e => e.Rating), entries.Count);
    }
}
