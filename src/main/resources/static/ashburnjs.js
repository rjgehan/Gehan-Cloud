// Add a new row
function addFoodRow() {
  const container = document.querySelector('.flex-container-food');

  // Create a new row element
  const row = document.createElement('div');
  row.className = 'food-row';
  row.innerHTML = `
      <div class="food-info">
        <div class="food-name">New Place</div>
        <div class="food-meta">
          <span class="tag"><i class="mdi mdi-silverware-fork-knife"></i> Cuisine</span>
          <span class="tag"><i class="mdi mdi-cash-multiple"></i> $0.00</span>
        </div>
      </div>
      <div class="food-score ok" data-score="70">70</div>
      <div class="food-details">Entree: Example Dish</div>
    `;

  // Add click toggle for expanding details
  row.onclick = () => row.classList.toggle('is-open');

  // Append it to the list
  container.appendChild(row);
}

// Delete the last row
function deleteLastFoodRow() {
  const container = document.querySelector('.flex-container-food');
  if (container.lastElementChild) {
    container.removeChild(container.lastElementChild);
  }
}

// Edit the first row (example: change name)
function editFirstFoodRow() {
  const firstRow = document.querySelector('.food-row');
  if (!firstRow) return;

  const nameEl = firstRow.querySelector('.food-name');
  const newName = prompt('Edit restaurant name:', nameEl.textContent);
  if (newName) nameEl.textContent = newName;
}

function addFoodRow() {
  document.getElementById('addFoodModal').classList.remove('hidden');
}

function closeModal() {
  document.getElementById('addFoodModal').classList.add('hidden');
}

function saveFoodRow() {
  const name = document.getElementById('foodName').value.trim();
  const cuisine = document.getElementById('foodCuisine').value.trim();
  const price = document.getElementById('foodPrice').value.trim();
  const score = Number(document.getElementById('foodScore').value.trim());
  const icon = document.getElementById('foodIcon').value;

  if (!name || !cuisine || !price) {
    alert("Please fill all fields");
    return;
  }

  const container = document.querySelector('.flex-container-food');
  const row = document.createElement('div');
  row.className = 'food-row';
  row.onclick = () => row.classList.toggle('is-open');

  row.innerHTML = `
    <div class="food-info">
      <div class="food-name">${name}</div>
      <div class="food-meta">
        <span class="tag"><i class="mdi ${icon}"></i> ${cuisine}</span>
        <span class="tag"><i class="mdi mdi-cash-multiple"></i> ${price}</span>
      </div>
    </div>
    <div class="food-score" data-score="${score}">${score}</div>
    <div class="food-details">Entree: Example Dish</div>
  `;

  container.appendChild(row);
  closeModal();
}
