using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.RazorPages;
using FlowSenseWeb.Models;
using FlowSenseWeb.Services;

namespace FlowSenseWeb.Pages;

public class IndexModel : PageModel
{
    private readonly FeedbackService _feedbackService;

    public IndexModel(FeedbackService feedbackService)
    {
        _feedbackService = feedbackService;
    }

    [BindProperty]
    public FeedbackInputModel Input { get; set; } = new();

    public List<FeedbackEntry> Reviews { get; set; } = [];
    public double AverageRating { get; set; }
    public int ReviewCount { get; set; }
    public bool FeedbackSubmitted { get; set; }

    public async Task OnGetAsync()
    {
        await LoadFeedbackAsync();
    }

    public async Task<IActionResult> OnPostAsync()
    {
        if (!ModelState.IsValid)
        {
            await LoadFeedbackAsync();
            return Page();
        }

        var entry = new FeedbackEntry
        {
            Name = Input.Name.Trim(),
            Rating = Input.Rating,
            Comment = Input.Comment.Trim()
        };

        await _feedbackService.AddAsync(entry);
        FeedbackSubmitted = true;
        await LoadFeedbackAsync();
        return Page();
    }

    private async Task LoadFeedbackAsync()
    {
        Reviews = await _feedbackService.GetAllAsync();
        var stats = await _feedbackService.GetStatsAsync();
        AverageRating = stats.Average;
        ReviewCount = stats.Count;
    }
}
