using System.ComponentModel.DataAnnotations;

namespace FlowSenseWeb.Models;

public class FeedbackEntry
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public string Name { get; set; } = string.Empty;
    public int Rating { get; set; }
    public string Comment { get; set; } = string.Empty;
    public DateTime SubmittedAt { get; set; } = DateTime.UtcNow;
}

public class FeedbackInputModel
{
    [Required(ErrorMessage = "Name is required.")]
    [StringLength(80, MinimumLength = 2, ErrorMessage = "Name must be 2–80 characters.")]
    public string Name { get; set; } = string.Empty;

    [Range(1, 5, ErrorMessage = "Please select a rating between 1 and 5.")]
    public int Rating { get; set; }

    [Required(ErrorMessage = "Comment is required.")]
    [StringLength(1000, MinimumLength = 5, ErrorMessage = "Comment must be 5–1000 characters.")]
    public string Comment { get; set; } = string.Empty;
}
